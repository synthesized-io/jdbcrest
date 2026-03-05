package io.synthesized.e2e;


import io.restassured.specification.RequestSpecification;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.Matchers.equalTo;

@ExtendWith(ComparisonTestExtension.class)
public class ComparisonTest {
    @TestTemplate
    void emptyTableReturnsEmptyList(RequestSpecification request) {
        request
                .when()
                .get("empty_table")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @TestTemplate
    void usersReturnsUsers(RequestSpecification request) {
        request
                .when()
                .get("users")
                .then()
                .statusCode(200)
                .body("size()", Matchers.greaterThanOrEqualTo(2))
                .body("[0].id", equalTo(1))
                .body("[0].name", equalTo("Alice"))
                .body("[1].id", equalTo(2))
                .body("[1].name", equalTo("Bob"));
    }

    @TestTemplate
    void usersEq(RequestSpecification request) {
        request
                .when()
                .get("users?name=eq.Bob")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(2))
                .body("[0].name", equalTo("Bob"));
    }

    @TestTemplate
    void usersGt(RequestSpecification request) {
        request
                .when()
                .get("users?id=gt.1")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(2))
                .body("[0].name", equalTo("Bob"));
    }

    @TestTemplate
    void usersGte(RequestSpecification request) {
        request
                .when()
                .get("users?id=gte.2")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(2))
                .body("[0].name", equalTo("Bob"));
    }

    @TestTemplate
    void usersLt(RequestSpecification request) {
        request
                .when()
                .get("users?id=lt.2")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(1))
                .body("[0].name", equalTo("Alice"));
    }

    @TestTemplate
    void usersLte(RequestSpecification request) {
        request
                .when()
                .get("users?id=lte.1")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(1))
                .body("[0].name", equalTo("Alice"));
    }

    @TestTemplate
    void usersTwoConditions(RequestSpecification request) {
        request
                .when()
                .get("users?id=gte.1&id=lt.2")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(1))
                .body("[0].name", equalTo("Alice"));
    }

    @TestTemplate
    void usersInOperator(RequestSpecification request) {
        request
                .when()
                .get("products?id=in.(100,102,103)")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("id", Matchers.containsInAnyOrder(100, 102))
                .body("title", Matchers.containsInAnyOrder("Apples", "Carrots"));
    }
}
