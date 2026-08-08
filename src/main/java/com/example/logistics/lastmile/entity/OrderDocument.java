package com.example.logistics.lastmile.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Elasticsearch 订单文档映射。
 * 和 JPA Order 实体分开——ES 的索引映射和 MySQL 表结构设计思路不同。
 *
 * <p>字段设计原则：
 * <ul>
 *   <li>需要全文搜索的字段用 {@code text}（会被 IK 分词器切词）</li>
 *   <li>精确匹配/排序/聚合的字段用 {@code keyword}（不切词，完整匹配）</li>
 *   <li>不搜索的字段用默认映射或设 {@code index = false}</li>
 * </ul>
 */
@Document(indexName = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDocument {

    @Id
    private Long id;

    /**
     * text + IK 分词：搜索"张三"能命中"张三丰"，搜索"朝阳"能命中"朝阳区"。
     * keyword 子字段：支持精确排序/聚合（如按客户名分组统计）。
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String customerName;

    /**
     * 地址也做分词搜索，方便搜"北京市"找到所有北京订单。
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String address;

    /**
     * 手机号用 keyword：不切词，精确匹配。"138" 不应该匹配 "139"。
     */
    @Field(type = FieldType.Keyword)
    private String phone;

    /**
     * 状态用 keyword：精确匹配 + 聚合统计（按状态分组数个数）。
     */
    @Field(type = FieldType.Keyword)
    private String status;

    /**
     * 配送员 ID，keyword 足够。
     */
    @Field(type = FieldType.Keyword)
    private Long courierId;

    /**
     * 配送员姓名（冗余字段，方便搜索时显示，不用回 MySQL 查）。
     * 实际项目里要不要冗余取决于数据一致性要求。
     */
    @Field(type = FieldType.Keyword)
    private String courierName;

    /**
     * 创建时间，用于排序（最新订单排前面）。
     */
    @Field(type = FieldType.Date)
    private String createdAt;
}
