package com.ruoyi.campus.domain;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 商品收藏（校园淘）
 *
 * <p>表 {@code campus_favorite} 上有 {@code uk_user_product(user_id, product_id)} 唯一约束——
 * "同一用户重复收藏同一商品"在 DB 层就被挡住，服务层仍需显式判断以便给出友好提示，
 * 而不是把 {@code DuplicateKeyException} 直接抛给用户。</p>
 *
 * <p>{@link #productTitle} / {@link #productPrice} 等冗余字段由收藏列表的联表查询填充，
 * 用于"收藏夹"直接展示商品概要，避免前端再逐条回查商品。</p>
 *
 * @author campus-tao
 */
public class CampusFavorite {

    private Long favoriteId;
    private Long userId;
    private Long productId;
    private Date createTime;

    // ---- 联表冗余：仅收藏列表查询时填充 ----
    private String productTitle;
    private BigDecimal productPrice;
    private String productImages;
    private Integer productStatus;
    private String sellerName;

    public Long getFavoriteId() {
        return favoriteId;
    }

    public void setFavoriteId(Long favoriteId) {
        this.favoriteId = favoriteId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public void setProductTitle(String productTitle) {
        this.productTitle = productTitle;
    }

    public BigDecimal getProductPrice() {
        return productPrice;
    }

    public void setProductPrice(BigDecimal productPrice) {
        this.productPrice = productPrice;
    }

    public String getProductImages() {
        return productImages;
    }

    public void setProductImages(String productImages) {
        this.productImages = productImages;
    }

    public Integer getProductStatus() {
        return productStatus;
    }

    public void setProductStatus(Integer productStatus) {
        this.productStatus = productStatus;
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }
}
