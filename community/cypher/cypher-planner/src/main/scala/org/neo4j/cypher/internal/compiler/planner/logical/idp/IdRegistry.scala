/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
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
package org.neo4j.cypher.internal.compiler.planner.logical.idp

import scala.collection.immutable.BitSet
import scala.collection.mutable

// IdRegistry maintains a bidirectional mapping of elements of type I
// to "small integers" such that sets of elements of type I
// can be stored efficiently using immutable BitSets
//
trait IdRegistry[I] {
  /**
   * @return `true` if this Registry contains compacted bits.
   */
  def compacted(): Boolean

  /**
   * Register elem
   * @return it's assigned id
   */
  def register(elem: I): Int

  /**
   * reverse lookup for registered elements only
   * (i.e. ignores compaction)
   */
  def lookup(id: Int): Option[I]


  /**
   * register all elements and
   * return a bit set with all bits set that
   * correspond to the assigned ids for the elements
   * @return all assigned ids in a bitset
   */
  def registerAll(elements: Iterable[I]): BitSet


  /**
   * register a fresh id for a compacted bit set of previously returned ids
   * @return the new id
   */
  def compact(existing: BitSet): Int

  /**
   * reverse lookup for a whole bit set.
   * explode takes into account lookup as well as
   * compaction information, i.e. if the bit set
   * contains bits corresponding to ids returned by
   * compact, these are translated to regular elements
   * using previously recorded compaction information
   */
  def explode(ids: BitSet): Set[I]

  /**
   * Equivalent to `explode(ids).size` but more efficient.
   */
  def explodedSize(ids: BitSet): Int
}

object IdRegistry {
  def apply[I] = new DefaultIdRegistry[I]
}

class DefaultIdRegistry[I] extends IdRegistry[I] {
  // Index 0 is reserved for "sorted", so we start with index 1, after incrementing count once.
  private var count = 0
  private val map = new mutable.HashMap[I, Int]
  private val reverseMap = new mutable.HashMap[Int, I]
  private val compactionMap = new mutable.HashMap[Int, BitSet]

  override def registerAll(elems: Iterable[I]): BitSet = {
    val builder = BitSet.newBuilder
    builder.sizeHint(elems)
    val iterator = elems.iterator
    while (iterator.hasNext) {
      builder += register(iterator.next())
    }
    builder.result()
  }

  override def register(elem: I): Int = {
    val oldId = map.getOrElse(elem, -1)
    if (oldId == -1) {
      val newId = registerNew()
      map.put(elem, newId)
      reverseMap.put(newId, elem)
      newId
    } else {
      oldId
    }
  }

  override def compact(existing: BitSet): Int = {
    val newId = registerNew()
    compactionMap.put(newId, existing)
    newId
  }

  private def registerNew(): Int = { count += 1; count }

  override def explode(ids: BitSet): Set[I] = {
    val builder = Set.newBuilder[I]
    ids.foreach(id => translate(id, builder))
    builder.result()
  }

  override def explodedSize(ids: BitSet): Int = {
    ids.toSeq.map { id =>
      reverseMap.get(id) match {
        case Some(_) => 1
        case None => explodedSize(compactionMap(id))
      }
    }.sum
  }

  override def lookup(id: Int): Option[I] =
    reverseMap.get(id)

  private def translate(id: Int, target: mutable.Builder[I, Set[I]]): Set[I] = {
    reverseMap.get(id) match {
      case Some(elem) =>
        target += elem

      case None =>
        compactionMap(id).foreach(id => translate(id, target))
    }
    target.result()
  }

  override def compacted(): Boolean = compactionMap.nonEmpty
}


