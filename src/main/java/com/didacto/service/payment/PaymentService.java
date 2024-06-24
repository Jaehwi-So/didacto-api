package com.didacto.service.payment;

import com.didacto.domain.Order;


import com.didacto.dto.pay.PayResponse;
import com.didacto.dto.pay.PaymentCallbackRequest;
import com.didacto.dto.pay.WebhookRequest;
import com.didacto.repository.member.MemberRepository;
import com.didacto.repository.order.OrderRepository;
import com.didacto.repository.pament.PaymentRepository;
import com.siot.IamportRestClient.IamportClient;
import com.siot.IamportRestClient.exception.IamportResponseException;
import com.siot.IamportRestClient.request.CancelData;
import com.siot.IamportRestClient.response.IamportResponse;
import com.siot.IamportRestClient.response.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;

@Service
@Transactional
@RequiredArgsConstructor
public class PaymentService{
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final IamportClient iamportClient;

    public PayResponse findRequestDto(String orderUid) {
        Order order = orderRepository.findOrderAndPaymentAndMember(orderUid)
                .orElseThrow(() -> new IllegalArgumentException("주문이 없습니다."));


        return PayResponse.builder()
                .buyerName(order.getMember().getName())
                .buyerEmail(order.getMember().getEmail())
                .paymentPrice(order.getPayment().getPrice())
                .itemName(order.getItemName())
                .orderUid(order.getOrderUid())
                .build();
    }

    @Transactional
    public void processWebhook(WebhookRequest webhookRequest) {
        try {
            IamportResponse<Payment> iamportResponse = iamportClient.paymentByImpUid(webhookRequest.getPaymentUid());

            // 주문내역 조회
            Order order = orderRepository.findOrderAndPayment(webhookRequest.getOrderUid())
                    .orElseThrow(() -> new IllegalArgumentException("주문 내역이 없습니다."));

            if ("paid".equals(iamportResponse.getResponse().getStatus())) {
                // 결제 성공 시 처리 로직
                order.getPayment().changePaymentBySuccess(iamportResponse.getResponse().getStatus(), webhookRequest.getPaymentUid());
                order.getMember().premium();
            } else {
                // 결제 실패 시 처리 로직
                orderRepository.delete(order);
                paymentRepository.delete(order.getPayment());
                throw new RuntimeException("결제 실패: " + iamportResponse.getResponse().getFailReason());
            }
        } catch (IamportResponseException | IOException e) {
            throw new RuntimeException("결제 처리 중 오류 발생", e);
        }
    }

    @Transactional
    public IamportResponse<Payment> paymentByCallback(PaymentCallbackRequest request) {
        try {
            // 결제 단건 조회(아임포트)
            IamportResponse<Payment> iamportResponse = iamportClient.paymentByImpUid(request.getPaymentUid());
            // 주문내역 조회
            Order order = orderRepository.findOrderAndPayment(request.getOrderUid())
                    .orElseThrow(() -> new IllegalArgumentException("주문 내역이 없습니다."));

            // 결제 완료가 아니면
            if(!iamportResponse.getResponse().getStatus().equals("paid")) {
                // 주문, 결제 삭제
                orderRepository.delete(order);
                paymentRepository.delete(order.getPayment());
                throw new RuntimeException("결제 미완료");
            }

            // DB에 저장된 결제 금액
            Long price = order.getPayment().getPrice();
            // 실 결제 금액
            int iamportPrice = iamportResponse.getResponse().getAmount().intValue();


            // 결제 금액 검증
            if(iamportPrice != price) {
                // 주문, 결제 삭제
                orderRepository.delete(order);
                paymentRepository.delete(order.getPayment());

                // 결제금액 위변조로 의심되는 결제금액을 취소(아임포트)
                iamportClient.cancelPaymentByImpUid(new CancelData(iamportResponse.getResponse().getImpUid(), true, new BigDecimal(iamportPrice)));

                throw new RuntimeException("결제금액 위변조 의심");
            }

            // 결제 상태 변경
            order.getPayment().changePaymentBySuccess(iamportResponse.getResponse().getStatus(), iamportResponse.getResponse().getImpUid());
            order.getMember().premium();

            return iamportResponse;

        } catch (IamportResponseException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}