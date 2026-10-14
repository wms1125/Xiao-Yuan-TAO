-- ============================================================
-- 校园淘 业务表（二）：购物车 / 支付沙箱 / 订单 / 售后 / 评价
-- 数据库：campus_tao
-- ============================================================

-- 购物车 campus_cart
DROP TABLE IF EXISTS campus_cart;
CREATE TABLE campus_cart (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '购物车ID',
  user_id      BIGINT       NOT NULL COMMENT '用户ID',
  user_name    VARCHAR(50)  DEFAULT '' COMMENT '用户名',
  product_id   BIGINT       NOT NULL COMMENT '商品ID',
  quantity     INT          NOT NULL DEFAULT 1 COMMENT '数量',
  checked      TINYINT      NOT NULL DEFAULT 1 COMMENT '是否勾选(0否1是)',
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车';

-- 支付沙箱账户 campus_account
DROP TABLE IF EXISTS campus_account;
CREATE TABLE campus_account (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '账户ID',
  user_id     BIGINT       NOT NULL COMMENT '用户ID',
  user_name   VARCHAR(50)  DEFAULT '' COMMENT '用户名',
  balance     DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '可用余额',
  freeze      DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '冻结金额(支付中)',
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付沙箱账户';

-- 订单 campus_order
DROP TABLE IF EXISTS campus_order;
CREATE TABLE campus_order (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
  order_no     VARCHAR(32)  NOT NULL COMMENT '订单编号(唯一)',
  user_id      BIGINT       NOT NULL COMMENT '买家ID',
  user_name    VARCHAR(50)  DEFAULT '' COMMENT '买家名',
  total_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '订单总额',
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0待付款 1已付款 2已发货 3已完成 4已取消 5退款中 6已退款',
  receiver_name  VARCHAR(50) DEFAULT '' COMMENT '收货人',
  receiver_phone VARCHAR(20) DEFAULT '' COMMENT '收货电话',
  address      VARCHAR(200) DEFAULT '' COMMENT '收货地址',
  remark       VARCHAR(255) DEFAULT '' COMMENT '买家备注',
  pay_time     DATETIME     DEFAULT NULL COMMENT '支付时间',
  ship_time    DATETIME     DEFAULT NULL COMMENT '发货时间',
  finish_time  DATETIME     DEFAULT NULL COMMENT '完成时间',
  cancel_time  DATETIME     DEFAULT NULL COMMENT '取消时间',
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_user (user_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单';

-- 订单项 campus_order_item
DROP TABLE IF EXISTS campus_order_item;
CREATE TABLE campus_order_item (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单项ID',
  order_id     BIGINT       NOT NULL COMMENT '订单ID',
  order_no     VARCHAR(32)  NOT NULL COMMENT '订单编号',
  product_id   BIGINT       NOT NULL COMMENT '商品ID',
  product_title VARCHAR(200) DEFAULT '' COMMENT '商品标题快照',
  image        VARCHAR(500) DEFAULT '' COMMENT '商品图片快照',
  price        DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '成交单价',
  quantity     INT          NOT NULL DEFAULT 1 COMMENT '数量',
  subtotal     DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '小计',
  KEY idx_order (order_id),
  KEY idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单项';

-- 售后 campus_aftersale
DROP TABLE IF EXISTS campus_aftersale;
CREATE TABLE campus_aftersale (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '售后ID',
  aftersale_no VARCHAR(32)  NOT NULL COMMENT '售后单号',
  order_id     BIGINT       NOT NULL COMMENT '订单ID',
  order_no     VARCHAR(32)  NOT NULL COMMENT '订单编号',
  user_id      BIGINT       NOT NULL COMMENT '申请人ID',
  user_name    VARCHAR(50)  DEFAULT '' COMMENT '申请人',
  type         TINYINT      NOT NULL DEFAULT 1 COMMENT '类型 1仅退款 2退货退款',
  reason       VARCHAR(255) DEFAULT '' COMMENT '原因',
  refund_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '退款金额',
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0待审核 1通过 2拒绝 3已完成',
  handler      VARCHAR(50)  DEFAULT '' COMMENT '处理人',
  handle_remark VARCHAR(255) DEFAULT '' COMMENT '处理备注',
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  handle_time  DATETIME     DEFAULT NULL COMMENT '处理时间',
  KEY idx_order (order_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后';

-- 评价 campus_review
DROP TABLE IF EXISTS campus_review;
CREATE TABLE campus_review (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评价ID',
  order_id    BIGINT       NOT NULL COMMENT '订单ID',
  product_id  BIGINT       NOT NULL COMMENT '商品ID',
  user_id     BIGINT       NOT NULL COMMENT '评价人ID',
  user_name   VARCHAR(50)  DEFAULT '' COMMENT '评价人',
  rating      TINYINT      NOT NULL DEFAULT 5 COMMENT '评分 1-5',
  content     VARCHAR(500) DEFAULT '' COMMENT '评价内容',
  images      VARCHAR(500) DEFAULT '' COMMENT '评价图片',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY idx_product (product_id),
  KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价';
