-- 校园淘业务表（M3 商品管理起步）
-- 在已存在的 campus_tao 库中执行
USE campus_tao;

DROP TABLE IF EXISTS campus_category;
CREATE TABLE campus_category (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  name          VARCHAR(50)  NOT NULL COMMENT '分类名称',
  parent_id     BIGINT       DEFAULT 0 COMMENT '父分类ID(0=一级)',
  sort          INT          DEFAULT 0 COMMENT '排序',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类';

DROP TABLE IF EXISTS campus_product;
CREATE TABLE campus_product (
  id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  category_id     BIGINT        DEFAULT 0 COMMENT '分类ID',
  title           VARCHAR(200)  NOT NULL COMMENT '商品标题',
  price           DECIMAL(10,2) NOT NULL COMMENT '售价',
  original_price  DECIMAL(10,2) DEFAULT NULL COMMENT '原价',
  condition_level INT           DEFAULT 5 COMMENT '成色(1-5,5=最新)',
  description     VARCHAR(2000) DEFAULT '' COMMENT '描述',
  images          VARCHAR(1000) DEFAULT '' COMMENT '图片URL,逗号分隔',
  stock           INT           NOT NULL DEFAULT 0 COMMENT '库存',
  seller_id       BIGINT        DEFAULT 0 COMMENT '卖家用户ID',
  seller_name     VARCHAR(50)   DEFAULT '' COMMENT '卖家昵称',
  status          TINYINT       DEFAULT 1 COMMENT '状态(0下架,1上架)',
  version         INT           DEFAULT 0 COMMENT '乐观锁版本号',
  create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_category (category_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='二手商品';
