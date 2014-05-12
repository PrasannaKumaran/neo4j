/**
 * Copyright (c) 2002-2014 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compiler.v2_1.planner.logical.steps

import org.neo4j.cypher.internal.commons.CypherFunSuite
import org.neo4j.cypher.internal.compiler.v2_1.planner._
import org.neo4j.cypher.internal.compiler.v2_1.ast
import org.neo4j.cypher.internal.compiler.v2_1.pipes.SortDescription
import org.neo4j.cypher.internal.compiler.v2_1.planner.logical.plans._
import org.neo4j.cypher.internal.compiler.v2_1.planner.logical.LogicalPlanningContext
import org.neo4j.cypher.internal.compiler.v2_1.ast.UnsignedIntegerLiteral
import org.neo4j.cypher.internal.compiler.v2_1.pipes.Ascending
import org.neo4j.cypher.internal.compiler.v2_1.ast.AscSortItem

class SortSkipAndLimitTest extends CypherFunSuite with LogicalPlanningTestSupport {

  import QueryPlanProducer._

  val x: ast.Expression = ast.UnsignedIntegerLiteral("110") _
  val y: ast.Expression = ast.UnsignedIntegerLiteral("10") _
  val identifierSortItem: AscSortItem = ast.AscSortItem(ast.Identifier("n") _) _
  val sortDescription: SortDescription = Ascending("n")

  test("should add skip if query graph contains skip") {
    // given
    implicit val (context, startPlan) = queryGraphWith(
      skip = Some(x)
    )

    // when
    val result = sortSkipAndLimit(startPlan)

    // then
    result.plan should equal(Skip(startPlan.plan, x))
    result.solved.projection.skip should equal(Some(x))
  }

  test("should add limit if query graph contains limit") {
    // given
    implicit val (context, startPlan) = queryGraphWith(
      limit = Some(x)
    )

    // when
    val result = sortSkipAndLimit(startPlan)

    // then
    result.plan should equal(Limit(startPlan.plan, x))
    result.solved.projection.limit should equal(Some(x))
  }

  test("should add skip first and then limit if the query graph contains both") {
    // given
    implicit val (context, startPlan) = queryGraphWith(
      limit = Some(x),
      skip = Some(y)
    )

    // when
    val result = sortSkipAndLimit(startPlan)

    // then
    result.plan should equal(Limit(Skip(startPlan.plan, y), x))
    result.solved.projection.limit should equal(Some(x))
    result.solved.projection.skip should equal(Some(y))
  }

  test("should add sort if query graph contains sort items") {
    // given
    implicit val (context, startPlan) = queryGraphWith(
      sortItems = Seq(identifierSortItem)
    )

    // when
    val result = sortSkipAndLimit(startPlan)

    // then
    result.plan should equal(Sort(startPlan.plan, Seq(sortDescription)))
    result.solved.projection.sortItems should equal(Seq(identifierSortItem))
  }

  test("should add projection before sort if query graph contains sort items that are not identifiers") {
    // given
    val exp: ast.Expression = ast.Add(UnsignedIntegerLiteral("10") _, UnsignedIntegerLiteral("10") _) _
    val expressionSortItem: AscSortItem = ast.AscSortItem(exp) _

    implicit val (context, startPlan) = queryGraphWith(
      sortItems = Seq(expressionSortItem)
    )

    // when
    val result = sortSkipAndLimit(startPlan)

    // then
    result.solved.projection.sortItems should equal(Seq(expressionSortItem))

    result.plan should equal(
      Sort(
        left = Projection(
          left = startPlan.plan,
          expressions = Map("  FRESHID0" -> exp, "n" -> ast.Identifier("n") _)
        ),
        sortItems = Seq(Ascending("  FRESHID0"))
      )
    )
  }

  test("should add projection before sort with mixed identifier and non-identifier expressions") {
    // given
    val exp: ast.Expression = ast.Add(UnsignedIntegerLiteral("10") _, UnsignedIntegerLiteral("10") _) _
    val expressionSortItem: AscSortItem = ast.AscSortItem(exp) _

    implicit val (context, startPlan) = queryGraphWith(
      sortItems = Seq(expressionSortItem, identifierSortItem)
    )

    // when
    val result = sortSkipAndLimit(startPlan)

    // then
    result.solved.projection.sortItems should equal(Seq(expressionSortItem, identifierSortItem))

    result.plan should equal(
      Sort(
        left = Projection(
          left = startPlan.plan,
          expressions = Map("  FRESHID0" -> exp, "n" -> ast.Identifier("n") _)
        ),
        sortItems = Seq(Ascending("  FRESHID0"), Ascending("n"))
      )
    )
  }

  test("should add SortedLimit when query uses both ORDER BY and LIMIT") {
    val sortItems: Seq[AscSortItem] = Seq(identifierSortItem)
    // given
    implicit val (context, startPlan) = queryGraphWith(
      sortItems = sortItems,
      limit = Some(x)
    )

    // when
    val result = sortSkipAndLimit(startPlan)

    // then
    result should equal(
      planSortedLimit(startPlan, x, sortItems)
    )

    result.solved.projection.limit should equal(Some(x))
    result.solved.projection.sortItems should equal(sortItems)
  }

  test("should add SortedLimit when query uses both ORDER BY and LIMIT, and add the SKIP value to the SortedLimit") {
    // given
    implicit val (context, startPlan) = queryGraphWith(
      sortItems = Seq(identifierSortItem),
      limit = Some(x),
      skip = Some(y)
    )

    // when
    val result = sortSkipAndLimit(startPlan)

    // then
    result should equal(
      planSortedSkipAndLimit(
        startPlan,
        y,
        x,
        Seq(identifierSortItem)
      )
    )

    result.solved.projection.limit should equal(Some(x))
    result.solved.projection.skip should equal(Some(y))
    result.solved.projection.sortItems should equal(Seq(identifierSortItem))
  }

  private def queryGraphWith(skip: Option[ast.Expression] = None,
                             limit: Option[ast.Expression] = None,
                             sortItems: Seq[ast.SortItem] = Seq.empty,
                             projectionsMap: Map[String, ast.Expression] = Map("n" -> ast.Identifier("n")(pos))): (LogicalPlanningContext, QueryPlan) = {
    val projections = QueryProjection(
      limit = limit,
      skip = skip,
      sortItems = sortItems,
      projections = projectionsMap)

    val qg = QueryGraph(patternNodes = Set(IdName("n")))

    val context = newMockedLogicalPlanningContext(
      planContext = newMockedPlanContext,
      query = PlannerQuery(qg, projections)
    )

    val plan = QueryPlan(
      newMockedLogicalPlan("n"),
      PlannerQuery(QueryGraph.empty.addPatternNodes(IdName("n")))
    )

    (context, plan)
  }
}
