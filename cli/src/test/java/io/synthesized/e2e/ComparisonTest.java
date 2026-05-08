package io.synthesized.e2e;


import io.restassured.specification.RequestSpecification;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

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

    @TestTemplate
    void orOperator(RequestSpecification request) {
        request
                .when()
                .get("products?or=(id.eq.100,id.eq.102)")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("id", Matchers.hasItem(100))
                .body("id", Matchers.hasItem(102))
                .body("title", Matchers.hasItem("Apples"))
                .body("title", Matchers.hasItem("Carrots"));
    }

    @TestTemplate
    void andOperatorSingleOperand(RequestSpecification request) {
        request
                .when()
                .get("products?and=(id.eq.100)")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("id", Matchers.contains(100));
    }

    @TestTemplate
    void notOrOperator(RequestSpecification request) {
        request
                .when()
                .get("products?not.or=(id.eq.100, id.eq.200)")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("id", Matchers.not(Matchers.hasItem(100)))
                .body("id", Matchers.hasItem(101));
    }

    @TestTemplate
    void notEqOperator(RequestSpecification request) {
        request
                .when()
                .get("products?id=not.eq.100")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("id", Matchers.not(Matchers.hasItem(100)))
                .body("id", Matchers.hasItem(101));
    }

    @TestTemplate
    void limit(RequestSpecification request) {
        request
                .when()
                .get("products?limit=2")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("id", Matchers.hasItem(100));
    }

    @TestTemplate
    void limitOffset(RequestSpecification request) {
        request
                .when()
                .get("products?offset=1&limit=1")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("id", Matchers.not(Matchers.hasItem(100)));
    }

    @TestTemplate
    void selectColumns(RequestSpecification request) {
        request
                .when()
                .get("products?select=id,price")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("$", Matchers.everyItem(Matchers.allOf(
                        Matchers.hasKey("id"),
                        Matchers.hasKey("price"),
                        Matchers.not(Matchers.hasKey("title"))
                )))
                .body("id", Matchers.hasItem(100));
    }
    @TestTemplate
    void selectColumnsWithAliases(RequestSpecification request) {
        request
                .when()
                .get("products?select=c1:id,c2:price")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("$", Matchers.everyItem(Matchers.allOf(
                        Matchers.hasKey("c1"),
                        Matchers.hasKey("c2")
                )))
                .body("c1", Matchers.hasItem(100));
    }

    @TestTemplate
    void aggregateCount(RequestSpecification request) {
        request
                .when()
                .get("products?select=c:count()")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].c", equalTo(3));
    }

    @TestTemplate
    void aggregateSum(RequestSpecification request) {
        request
                .when()
                .get("products?select=sum1:price.sum()")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].sum1", equalTo(5.47F));
    }

    @TestTemplate
    void aggregateAvg(RequestSpecification request) {
        request
                .when()
                .get("products?select=price.avg()")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].avg", Matchers.is(Matchers.anything()));
    }

    @TestTemplate
    void aggregateMinMax(RequestSpecification request) {
        request
                .when()
                .get("products?select=min:price.min(),max:price.max()")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].min", equalTo(0.99F))
                .body("[0].max", equalTo(2.49F));
    }

    @TestTemplate
    void aggregateGroupBy(RequestSpecification request) {
        request
                .when()
                .get("products?select=price,count:id.count()")
                .then()
                .statusCode(200)
                .body("size()", equalTo(3))
                .body("find { it.price == 0.99 }.count", Matchers.is(Matchers.anything()));
    }
}
