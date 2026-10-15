package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ruoyi.campus.domain.CampusAftersale;
import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.domain.CampusOrderItem;
import com.ruoyi.campus.domain.vo.CampusStatusStat;
import com.ruoyi.campus.mapper.CampusAftersaleMapper;
import com.ruoyi.campus.mapper.CampusOrderItemMapper;
import com.ruoyi.campus.mapper.CampusOrderMapper;

/**
 * 校园淘 SQL 层集成测试（列表分页 / 概览聚合 / 批量查询）。
 *
 * <p>背景：列表改分页后新增了三段 SQL——`countGroupByStatus`（概览聚合）、
 * `selectByOrderNos`（消除列表 N+1）。它们此前只有 Mockito 打桩的 service 单测覆盖，
 * 能证明分支逻辑，却<b>不覆盖 SQL 的真实执行</b>：XML 里 group by 语义写错、
 * in 子句漏拼、`Filter_Where` 片段在聚合与列表两处漂移，现有测试都会全绿。
 *
 * <p>做法：不启动 Spring 上下文，用 MyBatis 直接加载<b>真实 mapper XML</b>
 * （{@code mapper/campus/*Mapper.xml}）连本地 MySQL 执行真 SQL，断言其业务语义。
 * 复用 {@code CampusAccountAtomicSqlTest} 的既有约定——库不可达时
 * {@code Assumptions.assumeTrue} 自动跳过，保证无 MySQL 的 CI job 依旧全绿。
 *
 * <p>核心断言口径：<b>聚合查询的结果必须与"先全量列表查询、再在内存里分组"的结果完全一致</b>。
 * 两者只要分叉，就说明「概览数字」与「列表内容」对不上——正是分页改造要防的回归。
 */
class CampusMapperSqlIntegrationTest {

    private static final String URL =
            "jdbc:mysql://localhost:3306/campus_tao?useUnicode=true&characterEncoding=utf8"
            + "&serverTimezone=GMT%2B8&useSSL=false&allowPublicKeyRetrieval=true";

    /** 专用测试身份，与演示数据（user_id 2~8）隔离，避免污染与误判 */
    private static final long USER_A = 909101L;
    private static final long USER_B = 909102L;
    /** 测试订单号前缀，清理与模糊匹配都以它为准 */
    private static final String NO_PREFIX = "JUNITSQL";

    private static SqlSessionFactory factory;
    private static Connection conn;

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isEmpty()) ? fallback : value;
    }

    @BeforeAll
    static void openDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(URL, env("DB_USERNAME", "root"), env("DB_PASSWORD", "123456"));
            conn.setAutoCommit(true);
            factory = buildSessionFactory();
        } catch (Exception e) {
            // 无本地 MySQL / 驱动时整类跳过，保证 backend job（无 MySQL service）不会因此失败
            Assumptions.assumeTrue(false, "跳过：本地未提供 campus_tao 库或 MyBatis/驱动不可用 -> " + e.getMessage());
        }
    }

    /**
     * 手工装配 MyBatis：数据源直连本地库，逐个解析真实 mapper XML。
     * 不引入 Spring，也不改动生产配置——测试只依赖仓库里真实存在的那份 XML。
     */
    private static SqlSessionFactory buildSessionFactory() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "com.mysql.cj.jdbc.Driver", URL, env("DB_USERNAME", "root"), env("DB_PASSWORD", "123456"));
        Environment environment = new Environment("campus-sql-it", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        for (String resource : new String[] {
                "mapper/campus/CampusOrderMapper.xml",
                "mapper/campus/CampusOrderItemMapper.xml",
                "mapper/campus/CampusAftersaleMapper.xml"}) {
            try (java.io.InputStream in = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void cleanTestData() throws Exception {
        // 订单项无 user_id，按订单号前缀清理；订单/售后按测试身份清理
        conn.createStatement().executeUpdate(
                "delete from campus_order_item where order_no like '" + NO_PREFIX + "%'");
        conn.createStatement().executeUpdate(
                "delete from campus_order where user_id in (" + USER_A + "," + USER_B + ")");
        conn.createStatement().executeUpdate(
                "delete from campus_aftersale where user_id in (" + USER_A + "," + USER_B + ")");
    }

    @AfterAll
    static void closeDatabase() throws Exception {
        if (conn != null) {
            conn.createStatement().executeUpdate(
                    "delete from campus_order_item where order_no like '" + NO_PREFIX + "%'");
            conn.createStatement().executeUpdate(
                    "delete from campus_order where user_id in (" + USER_A + "," + USER_B + ")");
            conn.createStatement().executeUpdate(
                    "delete from campus_aftersale where user_id in (" + USER_A + "," + USER_B + ")");
            conn.close();
        }
    }

    // ------------------------------------------------------------------
    // 概览聚合口径：countGroupByStatus 必须与"列表 + 内存分组"逐项一致
    // ------------------------------------------------------------------

    @Test
    @DisplayName("概览聚合口径 = 列表内存分组口径（按用户筛选）")
    void 概览聚合口径_与列表内存分组一致_按用户筛选() {
        try (SqlSession session = factory.openSession(true)) {
            CampusOrderMapper mapper = session.getMapper(CampusOrderMapper.class);
            insertOrder(mapper, USER_A, 0, "a1");
            insertOrder(mapper, USER_A, 0, "a2");
            insertOrder(mapper, USER_A, 0, "a3");
            insertOrder(mapper, USER_A, 1, "a4");
            insertOrder(mapper, USER_A, 1, "a5");
            insertOrder(mapper, USER_A, 3, "a6");
            // 同库另一用户：用来验证 userId 筛选确实生效，不会被别人订单稀释
            insertOrder(mapper, USER_B, 0, "b1");
            insertOrder(mapper, USER_B, 2, "b2");

            Map<Integer, Long> fromStats = statsByStatus(mapper.countGroupByStatus(filterByUser(USER_A)));
            Map<Integer, Long> fromList = groupByStatus(mapper.selectOrderList(filterByUser(USER_A)));

            assertEquals(Map.of(0, 3L, 1, 2L, 3, 1L), fromStats, "聚合结果应为 待付款3/已付款2/已完成1");
            assertEquals(fromList, fromStats, "聚合口径必须与列表内存分组口径完全一致，否则概览会与列表对不上");
        }
    }

    @Test
    @DisplayName("概览聚合口径 = 列表内存分组口径（叠加状态筛选）")
    void 概览聚合口径_与列表内存分组一致_叠加状态筛选() {
        try (SqlSession session = factory.openSession(true)) {
            CampusOrderMapper mapper = session.getMapper(CampusOrderMapper.class);
            insertOrder(mapper, USER_A, 0, "c1");
            insertOrder(mapper, USER_A, 0, "c2");
            insertOrder(mapper, USER_A, 0, "c3");
            insertOrder(mapper, USER_A, 1, "c4");
            insertOrder(mapper, USER_A, 3, "c5");

            CampusOrder filter = filterByUser(USER_A);
            filter.setStatus(0);
            Map<Integer, Long> fromStats = statsByStatus(mapper.countGroupByStatus(filter));
            List<CampusOrder> list = mapper.selectOrderList(filter);

            assertEquals(Map.of(0, 3L), fromStats, "叠加 status 筛选后，聚合只应返回该状态一行，且计数=3");
            assertEquals(3, list.size(), "同一筛选条件下列表应恰好 3 条");
            assertEquals(groupByStatus(list), fromStats, "聚合与列表在叠加筛选条件后仍须一致");
        }
    }

    @Test
    @DisplayName("Filter_Where 的订单号模糊筛选在聚合与列表间口径一致")
    void Filter_Where_订单号模糊筛选_聚合与列表一致() {
        try (SqlSession session = factory.openSession(true)) {
            CampusOrderMapper mapper = session.getMapper(CampusOrderMapper.class);
            insertOrder(mapper, USER_A, 0, "d1");
            insertOrder(mapper, USER_A, 0, "d2");
            insertOrder(mapper, USER_A, 1, "d3");
            insertOrder(mapper, USER_B, 0, "d4");

            CampusOrder hit = new CampusOrder();
            hit.setOrderNo(NO_PREFIX + "-" + USER_A);
            Map<Integer, Long> fromStats = statsByStatus(mapper.countGroupByStatus(hit));
            List<CampusOrder> list = mapper.selectOrderList(hit);

            assertEquals(3, list.size(), "订单号模糊匹配应命中 USER_A 的 3 单（USER_B 的单号不含该前缀）");
            assertEquals(groupByStatus(list), fromStats, "复用同一 Filter_Where 片段时，模糊筛选口径不得漂移");

            CampusOrder miss = new CampusOrder();
            miss.setOrderNo(NO_PREFIX + "-NO-SUCH");
            assertTrue(mapper.selectOrderList(miss).isEmpty(), "无命中时列表应为空");
            assertTrue(mapper.countGroupByStatus(miss).isEmpty(), "无命中时聚合不应返回任何分组行");
        }
    }

    @Test
    @DisplayName("空筛选条件：聚合仍与列表全量分组一致")
    void 空筛选条件_聚合与列表全量分组一致() {
        try (SqlSession session = factory.openSession(true)) {
            CampusOrderMapper mapper = session.getMapper(CampusOrderMapper.class);
            insertOrder(mapper, USER_A, 0, "e1");
            insertOrder(mapper, USER_A, 2, "e2");
            insertOrder(mapper, USER_B, 4, "e3");

            // 生产侧传入的是查询对象（字段可全空），此处等价构造一个"无任何条件"的过滤对象
            CampusOrder filter = new CampusOrder();
            Map<Integer, Long> fromStats = statsByStatus(mapper.countGroupByStatus(filter));
            List<CampusOrder> all = mapper.selectOrderList(filter);

            assertEquals(all.size(), fromStats.values().stream().mapToLong(Long::longValue).sum(),
                    "无筛选时聚合总数应等于全量列表条数（证明 <where> 片段未漏拼条件）");
            assertEquals(groupByStatus(all), fromStats, "无筛选时聚合与列表分组口径仍须一致");
        }
    }

    @Test
    @DisplayName("countByUserId 与同条件的列表条数一致")
    void countByUserId_与列表条数一致() {
        try (SqlSession session = factory.openSession(true)) {
            CampusOrderMapper mapper = session.getMapper(CampusOrderMapper.class);
            insertOrder(mapper, USER_A, 0, "f1");
            insertOrder(mapper, USER_A, 1, "f2");
            insertOrder(mapper, USER_A, 6, "f3");
            insertOrder(mapper, USER_B, 0, "f4");

            assertEquals(Integer.valueOf(3), mapper.countByUserId(USER_A), "countByUserId 应只统计该用户订单");
            assertEquals(mapper.selectOrderList(filterByUser(USER_A)).size(), mapper.countByUserId(USER_A),
                    "计数接口与列表接口的用户口径必须一致");
        }
    }

    // ------------------------------------------------------------------
    // 批量查询：消除 N+1 的 selectByOrderNos 必须与"逐单查询"结果完全一致
    // ------------------------------------------------------------------

    @Test
    @DisplayName("批量查询订单项 = 逐单查询结果（N+1 回归）")
    void 批量查询订单项_与逐单查询一致() {
        try (SqlSession session = factory.openSession(true)) {
            CampusOrderMapper orderMapper = session.getMapper(CampusOrderMapper.class);
            CampusOrderItemMapper itemMapper = session.getMapper(CampusOrderItemMapper.class);

            String no1 = insertOrder(orderMapper, USER_A, 3, "g1");
            String no2 = insertOrder(orderMapper, USER_A, 1, "g2");
            String no3 = insertOrder(orderMapper, USER_A, 0, "g3");
            itemMapper.insertCampusOrderItemBatch(itemsFor(no1, 2));
            itemMapper.insertCampusOrderItemBatch(itemsFor(no2, 1));
            itemMapper.insertCampusOrderItemBatch(itemsFor(no3, 3));

            List<String> orderNos = Arrays.asList(no1, no2, no3);
            List<CampusOrderItem> batch = itemMapper.selectByOrderNos(orderNos);

            // 一次批量取回后按订单号分组
            Map<String, List<CampusOrderItem>> batchByOrderNo = batch.stream()
                    .collect(Collectors.groupingBy(CampusOrderItem::getOrderNo));
            // 逐单查询作为基准
            Map<String, List<CampusOrderItem>> perOrder = new LinkedHashMap<>();
            for (String orderNo : orderNos) {
                perOrder.put(orderNo, itemMapper.selectByOrderNo(orderNo));
            }

            assertEquals(6, batch.size(), "批量查询应一次取回 3 单共 6 个订单项");
            assertEquals(perOrder.keySet(), batchByOrderNo.keySet(), "批量查询的订单号集合应与逐单一致");
            for (String orderNo : orderNos) {
                assertEquals(signaturesOf(perOrder.get(orderNo)), signaturesOf(batchByOrderNo.get(orderNo)),
                        "订单 " + orderNo + " 的订单项应与逐单查询逐字段一致（in 子句未漏拼/多拼）");
            }
        }
    }

    @Test
    @DisplayName("批量查询只取回传入的订单号，不串单")
    void 批量查询_不返回未传入的订单号() {
        try (SqlSession session = factory.openSession(true)) {
            CampusOrderMapper orderMapper = session.getMapper(CampusOrderMapper.class);
            CampusOrderItemMapper itemMapper = session.getMapper(CampusOrderItemMapper.class);

            String hit = insertOrder(orderMapper, USER_A, 3, "h1");
            String other = insertOrder(orderMapper, USER_A, 3, "h2");
            itemMapper.insertCampusOrderItemBatch(itemsFor(hit, 1));
            itemMapper.insertCampusOrderItemBatch(itemsFor(other, 4));

            List<CampusOrderItem> rows = itemMapper.selectByOrderNos(List.of(hit));
            assertEquals(1, rows.size(), "只传入 1 个订单号时不应取回其它订单的订单项");
            assertEquals(hit, rows.get(0).getOrderNo(), "返回行必须属于传入的订单号");
        }
    }

    // ------------------------------------------------------------------
    // 售后概览聚合：分组计数 + 退款金额合计
    // ------------------------------------------------------------------

    @Test
    @DisplayName("售后概览聚合：分组计数正确且退款金额合计与列表口径一致")
    void 售后概览聚合_退款金额合计与列表一致() {
        try (SqlSession session = factory.openSession(true)) {
            CampusAftersaleMapper mapper = session.getMapper(CampusAftersaleMapper.class);
            insertAftersale(mapper, USER_A, 0, "100.50");
            insertAftersale(mapper, USER_A, 0, "200.00");
            insertAftersale(mapper, USER_A, 1, "50.25");
            insertAftersale(mapper, USER_B, 0, "999.99");

            CampusAftersale filter = new CampusAftersale();
            filter.setUserId(USER_A);
            List<CampusStatusStat> stats = mapper.countGroupByStatus(filter);
            List<CampusAftersale> list = mapper.selectAftersaleList(filter);

            Map<Integer, Long> countByStatus = statsByStatus(stats);
            assertEquals(Map.of(0, 2L, 1, 1L), countByStatus, "售后聚合应按状态分组：待审核2/通过1");

            // 金额合计口径：聚合的 sum 必须等于列表内存求和
            Map<Integer, BigDecimal> amountFromStats = stats.stream().collect(Collectors.toMap(
                    CampusStatusStat::getStatus, CampusStatusStat::getAmount, (a, b) -> a, LinkedHashMap::new));
            Map<Integer, BigDecimal> amountFromList = new LinkedHashMap<>();
            for (CampusAftersale row : list) {
                amountFromList.merge(row.getStatus(), row.getRefundAmount(), BigDecimal::add);
            }
            assertEquals(amountFromList.keySet(), amountFromStats.keySet(), "金额合计的状态集合应与列表一致");
            for (Integer status : amountFromList.keySet()) {
                assertEquals(0, amountFromList.get(status).compareTo(amountFromStats.get(status)),
                        "状态 " + status + " 的退款金额合计必须与列表内存求和一致");
            }
            assertEquals(0, new BigDecimal("300.50").compareTo(amountFromStats.get(0)),
                    "待审核状态退款金额合计应为 100.50 + 200.00 = 300.50");
        }
    }

    @Test
    @DisplayName("售后聚合：状态筛选后与列表口径一致，且不串他人数据")
    void 售后聚合_状态筛选后与列表一致() {
        try (SqlSession session = factory.openSession(true)) {
            CampusAftersaleMapper mapper = session.getMapper(CampusAftersaleMapper.class);
            insertAftersale(mapper, USER_A, 0, "10.00");
            insertAftersale(mapper, USER_A, 2, "20.00");
            insertAftersale(mapper, USER_B, 0, "30.00");

            CampusAftersale filter = new CampusAftersale();
            filter.setUserId(USER_A);
            filter.setStatus(0);
            List<CampusAftersale> list = mapper.selectAftersaleList(filter);
            Map<Integer, Long> fromStats = statsByStatus(mapper.countGroupByStatus(filter));

            assertEquals(1, list.size(), "USER_A 待审核售后应恰好 1 条");
            assertEquals(Map.of(0, 1L), fromStats, "状态筛选后聚合只应返回该状态一行且计数=1");
            assertEquals(0, new BigDecimal("10.00").compareTo(
                    mapper.countGroupByStatus(filter).get(0).getAmount()),
                    "状态筛选后金额合计应只涵盖该状态的售后单");
        }
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    private static CampusOrder filterByUser(long userId) {
        CampusOrder filter = new CampusOrder();
        filter.setUserId(userId);
        return filter;
    }

    private String insertOrder(CampusOrderMapper mapper, long userId, int status, String suffix) {
        CampusOrder order = new CampusOrder();
        order.setOrderNo(NO_PREFIX + "-" + userId + "-" + suffix);
        order.setUserId(userId);
        order.setUserName("junit");
        order.setTotalAmount(new BigDecimal("10.00"));
        order.setStatus(status);
        order.setReceiverName("junit");
        order.setReceiverPhone("13800000000");
        order.setAddress("junit");
        order.setRemark("junit");
        assertEquals(1, mapper.insertCampusOrder(order), "插入测试订单应影响 1 行");
        return order.getOrderNo();
    }

    private List<CampusOrderItem> itemsFor(String orderNo, int count) {
        List<CampusOrderItem> items = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            CampusOrderItem item = new CampusOrderItem();
            item.setOrderId(orderIdOf(orderNo));
            item.setOrderNo(orderNo);
            item.setProductId((long) i);
            item.setProductTitle("测试商品" + i);
            item.setImage("/campus-img/test.jpg");
            item.setPrice(new BigDecimal(i * 10 + ".00"));
            item.setQuantity(1);
            item.setSubtotal(new BigDecimal(i * 10 + ".00"));
            items.add(item);
        }
        return items;
    }

    private Long orderIdOf(String orderNo) {
        try (SqlSession session = factory.openSession(true)) {
            CampusOrder order = session.getMapper(CampusOrderMapper.class).selectOrderByOrderNo(orderNo);
            return order == null ? null : order.getId();
        }
    }

    /** 售后单号为 varchar(32)，故用短序号保证唯一且不超长 */
    private static int aftersaleSeq = 0;

    private void insertAftersale(CampusAftersaleMapper mapper, long userId, int status, String refundAmount) {
        CampusAftersale row = new CampusAftersale();
        row.setAftersaleNo(NO_PREFIX + "-AS-" + (++aftersaleSeq));
        row.setOrderId(0L);
        row.setOrderNo(NO_PREFIX + "-" + userId + "-as");
        row.setUserId(userId);
        row.setUserName("junit");
        row.setType(1);
        row.setReason("junit");
        row.setRefundAmount(new BigDecimal(refundAmount));
        // 注意：insertCampusAftersale 不含 status 列，新建售后单一律落到默认值 0（待审核）——
        // 这是生产语义（申请即待审核）。要构造其它状态，须显式走 updateCampusAftersale。
        assertEquals(1, mapper.insertCampusAftersale(row), "插入测试售后单应影响 1 行");
        if (status != 0) {
            CampusAftersale upd = new CampusAftersale();
            upd.setId(row.getId());
            upd.setStatus(status);
            assertEquals(1, mapper.updateCampusAftersale(upd), "更新售后状态应影响 1 行");
        }
    }

    private static Map<Integer, Long> statsByStatus(List<CampusStatusStat> stats) {
        Map<Integer, Long> result = new LinkedHashMap<>();
        for (CampusStatusStat stat : stats) {
            result.put(stat.getStatus(), stat.getCount());
        }
        return result;
    }

    private static Map<Integer, Long> groupByStatus(List<CampusOrder> orders) {
        return orders.stream().collect(Collectors.groupingBy(
                CampusOrder::getStatus, LinkedHashMap::new, Collectors.counting()));
    }

    /**
     * 订单项逐字段签名。{@code CampusOrderItem} 未实现 equals（纯 POJO），
     * 批量查询与逐单查询返回的是不同实例，只能按字段值比较，否则断言会假绿/假红。
     */
    private static List<String> signaturesOf(List<CampusOrderItem> items) {
        return items.stream()
                .map(item -> item.getOrderId() + "|" + item.getOrderNo() + "|" + item.getProductId() + "|"
                        + item.getProductTitle() + "|" + item.getImage() + "|" + item.getPrice() + "|"
                        + item.getQuantity() + "|" + item.getSubtotal())
                .sorted()
                .collect(Collectors.toList());
    }
}
