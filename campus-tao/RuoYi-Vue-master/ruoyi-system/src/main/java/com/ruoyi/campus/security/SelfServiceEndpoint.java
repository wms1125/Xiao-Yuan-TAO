package com.ruoyi.campus.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自助端点标记（校园淘）
 *
 * <p>用于声明"该变更类端点<b>不需要按钮级权限</b>，因为只作用于当前登录用户本人的数据"。
 * 典型场景：标记自己的通知已读、修改自己的购物车数量。</p>
 *
 * <p><b>为什么需要这个标记</b>：权限注解覆盖守护（{@code CampusPermissionGuardTest}）
 * 要求所有 POST/PUT/DELETE 端点要么带 {@code @PreAuthorize}、要么显式标注匿名。
 * 若不给"自助端点"一个合法的表达方式，作者就只能二选一——要么给所有用户配一个
 * 语义上毫无意义的按钮权限，要么干脆不加注解而让守护失效。
 * 本标记把第三种选择显式化：<b>可以不带权限，但必须写下理由</b>，且理由会出现在代码评审里。</p>
 *
 * <p><b>使用前提</b>：被标注的端点必须从 {@code SecurityUtils.getUserId()} 取用户身份，
 * 并把 userId 下推到 SQL 的 where 条件中。仅靠"登录了"是不够的——
 * 若查询条件里没有 user_id，"自助"就会变成"越权操作他人数据"。</p>
 *
 * @author campus-tao
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SelfServiceEndpoint {

    /** 为什么该端点可以不带按钮级权限（写明数据隔离依据，供评审核对） */
    String value();
}
