/*
 * Copyright 2026 Synthesized Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.synthesized.e2e;


import io.restassured.specification.RequestSpecification;
import io.synthesized.e2e.comparison.JdbcrestBackends;
import org.atpfivt.comparison.api.ComparisonTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

@ComparisonTest(source = JdbcrestBackends.class)
public class ApiComparisonTest {
    @Test
    void emptyTableReturnsEmptyList(RequestSpecification request) {
        request
                .when()
                .get("empty_table")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
    void andOperatorSingleOperand(RequestSpecification request) {
        request
                .when()
                .get("products?and=(id.eq.100)")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("id", Matchers.contains(100));
    }

    @Test
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

    @Test
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

    @Test
    void limit(RequestSpecification request) {
        request
                .when()
                .get("products?limit=2")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("id", Matchers.hasItem(100));
    }

    @Test
    void limitOffset(RequestSpecification request) {
        request
                .when()
                .get("products?offset=1&limit=1")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("id", Matchers.not(Matchers.hasItem(100)));
    }

    @Test
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
    @Test
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

    @Test
    void aggregateCount(RequestSpecification request) {
        request
                .when()
                .get("products?select=c:count()")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].c", equalTo(3));
    }

    @Test
    void aggregateSum(RequestSpecification request) {
        request
                .when()
                .get("products?select=sum1:price.sum()")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].sum1", equalTo(5.47F));
    }

    @Test
    void aggregateAvg(RequestSpecification request) {
        request
                .when()
                .get("products?select=price.avg()")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].avg", Matchers.is(Matchers.anything()));
    }

    @Test
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

    @Test
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
