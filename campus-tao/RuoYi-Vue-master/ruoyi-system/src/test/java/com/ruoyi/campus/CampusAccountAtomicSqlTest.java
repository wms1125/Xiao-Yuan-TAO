package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 账户扣款/退款原子性测试（校园淘 P0 修复①）
 *
 * <p>直接以 JDBC 复用 Mapper 中的原子 UPDATE 语义，验证：
 * 余额充足才扣减（防并发超扣）、退款入账、事务回滚。
 * 不依赖 Spring 容器，可在无 Maven 环境下通过 JUnit Console 运行。
 */
class CampusAccountAtomicSqlTest {

    private static final String URL =
            "jdbc:mysql://localhost:3306/campus_tao?useUnicode=true&characterEncoding=utf8"
            + "&serverTimezone=GMT%2B8&useSSL=false&allowPublicKeyRetrieval=true";
    private static final long TEST_USER = 999001L;
    private static Connection conn;

    @BeforeAll
    static void openConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(URL, "root", "123456");
        } catch (Exception e) {
            // 无本地 MySQL 时自动跳过，保证 CI/无库环境不会因此失败
            Assumptions.assumeTrue(false, "跳过：本地未提供 campus_tao 库或 MySQL 驱动 -> " + e.getMessage());
        }
    }

    @BeforeEach
    void resetAccount() throws Exception {
        conn.setAutoCommit(true);
        exec("DELETE FROM campus_account WHERE user_id = ?", TEST_USER);
        exec("INSERT INTO campus_account(user_id, user_name, balance, freeze) VALUES(?, ?, ?, 0)",
                TEST_USER, "junit", new BigDecimal("100.00"));
    }

    @AfterAll
    static void closeConnection() throws Exception {
        if (conn != null) {
            exec("DELETE FROM campus_account WHERE user_id = ?", TEST_USER);
            conn.close();
        }
    }

    @Test
    @DisplayName("余额充足时原子扣款成功")
    void 余额充足_扣款成功() throws Exception {
        assertEquals(1, decrease(new BigDecimal("60.00")), "余额充足应成功扣款(影响行数=1)");
        assertEquals(0, new BigDecimal("40.00").compareTo(balance()), "扣款后余额应为 40.00");
    }

    @Test
    @DisplayName("余额不足时原子 SQL 拒绝扣款，余额不变（防超扣）")
    void 余额不足_拒绝扣款() throws Exception {
        BigDecimal before = balance();
        assertEquals(0, decrease(new BigDecimal("99999.00")), "余额不足时影响行数应为 0");
        assertEquals(0, before.compareTo(balance()), "余额不足时余额不应变化");
    }

    @Test
    @DisplayName("重复退款入账金额累加正确")
    void 退款入账_金额累加() throws Exception {
        increase(new BigDecimal("20.00"));
        increase(new BigDecimal("30.00"));
        assertEquals(0, new BigDecimal("150.00").compareTo(balance()), "两次退款后余额应为 150.00");
    }

    @Test
    @DisplayName("事务回滚后扣款被撤销")
    void 事务回滚_扣款撤销() throws Exception {
        BigDecimal before = balance();
        conn.setAutoCommit(false);
        try {
            decrease(new BigDecimal("10.00"));
            conn.rollback();
        } finally {
            conn.setAutoCommit(true);
        }
        assertEquals(0, before.compareTo(balance()), "回滚后余额应与扣款前一致");
    }

    /** 与 CampusAccountMapper.decreaseBalance 完全一致的原子扣减 SQL */
    private int decrease(BigDecimal amount) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "update campus_account set balance = balance - ?, update_time = sysdate()"
                + " where user_id = ? and balance >= ?")) {
            ps.setBigDecimal(1, amount);
            ps.setLong(2, TEST_USER);
            ps.setBigDecimal(3, amount);
            return ps.executeUpdate();
        }
    }

    /** 与 CampusAccountMapper.increaseBalance 一致的退款入账 SQL */
    private void increase(BigDecimal amount) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "update campus_account set balance = balance + ?, update_time = sysdate()"
                + " where user_id = ?")) {
            ps.setBigDecimal(1, amount);
            ps.setLong(2, TEST_USER);
            ps.executeUpdate();
        }
    }

    private BigDecimal balance() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "select balance from campus_account where user_id = ?")) {
            ps.setLong(1, TEST_USER);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private static void exec(String sql, Object... args) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            ps.executeUpdate();
        }
    }
}
