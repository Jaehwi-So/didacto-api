package com.didacto.domain;


import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long price;
    private String itemName; // 프리티어, 실버, 골드 등등..
    private String orderUid; //주문번호
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Builder
    public Order(Long price, String itemName, String orderUid, Member member, Payment payment) {
        this.price = price;
        this.itemName = itemName;
        this.orderUid = orderUid;
        this.member = member;
        this.payment = payment;
    }
}
