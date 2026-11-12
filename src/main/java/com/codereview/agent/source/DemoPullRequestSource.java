package com.codereview.agent.source;

import com.codereview.agent.model.ChangedFile;
import com.codereview.agent.model.PullRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * A hardcoded pull request with bugs planted on purpose, so the agent has something to find.
 */
@Component
public class DemoPullRequestSource implements PullRequestSource {

    private final List<PullRequest> pullRequests = List.of(demoPullRequest());

    @Override
    public Optional<PullRequest> find(int number) {
        return pullRequests.stream().filter(pr -> pr.number() == number).findFirst();
    }

    @Override
    public List<PullRequest> all() {
        return pullRequests;
    }

    private static PullRequest demoPullRequest() {
        ChangedFile payment = new ChangedFile(
                "src/main/java/com/shop/payments/PaymentService.java",
                """
                --- a/src/main/java/com/shop/payments/PaymentService.java
                +++ b/src/main/java/com/shop/payments/PaymentService.java
                @@ -8,3 +8,17 @@ public class PaymentService {
                         this.gateway = gateway;
                     }
                +
                +    public boolean isPaid(Order order) {
                +        String status = gateway.fetchStatus(order.getId());
                +        if (status == "PAID") {
                +            return true;
                +        }
                +        return false;
                +    }
                +
                +    public void refund(Order order) {
                +        try {
                +            gateway.refund(order.getId());
                +        } catch (Exception e) {}
                +    }
                 }
                """,
                """
                package com.shop.payments;

                public class PaymentService {

                    private final PaymentGateway gateway;

                    public PaymentService(PaymentGateway gateway) {
                        this.gateway = gateway;
                    }

                    public boolean isPaid(Order order) {
                        String status = gateway.fetchStatus(order.getId());
                        if (status == "PAID") {
                            return true;
                        }
                        return false;
                    }

                    public void refund(Order order) {
                        try {
                            gateway.refund(order.getId());
                        } catch (Exception e) {}
                    }
                }
                """);

        ChangedFile orders = new ChangedFile(
                "src/main/java/com/shop/orders/OrderController.java",
                """
                --- a/src/main/java/com/shop/orders/OrderController.java
                +++ b/src/main/java/com/shop/orders/OrderController.java
                @@ -11,3 +11,9 @@ public class OrderController {
                         this.orderService = orderService;
                     }
                +
                +    @GetMapping("/orders/{id}")
                +    public Order getOrder(@PathVariable long id) {
                +        System.out.println("Fetching order " + id);
                +        return orderService.find(id);
                +    }
                 }
                """,
                """
                package com.shop.orders;

                import org.springframework.web.bind.annotation.*;

                @RestController
                public class OrderController {

                    private final OrderService orderService;

                    public OrderController(OrderService orderService) {
                        this.orderService = orderService;
                    }

                    @GetMapping("/orders/{id}")
                    public Order getOrder(@PathVariable long id) {
                        System.out.println("Fetching order " + id);
                        return orderService.find(id);
                    }
                }
                """);

        ChangedFile users = new ChangedFile(
                "src/main/java/com/shop/users/UserUtils.java",
                """
                --- a/src/main/java/com/shop/users/UserUtils.java
                +++ b/src/main/java/com/shop/users/UserUtils.java
                @@ -5,3 +5,7 @@ public final class UserUtils {
                     private UserUtils() {
                     }
                +
                +    public static String displayName(String first, String last) {
                +        return first + " " + last;
                +    }
                 }
                """,
                """
                package com.shop.users;

                public final class UserUtils {

                    private UserUtils() {
                    }

                    public static String displayName(String first, String last) {
                        return first + " " + last;
                    }
                }
                """);

        return new PullRequest(1, "Add payment status check, refunds, and order lookup",
                List.of(payment, orders, users));
    }
}
