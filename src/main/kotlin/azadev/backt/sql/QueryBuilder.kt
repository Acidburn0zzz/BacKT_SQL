@file:Suppress("unused")

package azadev.backt.sql

import azadev.backt.sql.utils.escapeSqlIdentifier
import azadev.backt.sql.utils.escapeSqlLiteral
import java.sql.ResultSet
import java.util.*


class QueryBuilder(
		val quoteIdentifiers: Boolean = true
) {
	val sb = StringBuilder(15) // SELECT * FROM t

	var params: ArrayList<Any?>? = null
	val paramArray: Array<Any?> get() = params?.toArray() ?: emptyArray()

	var hasSet = false
	var hasWhere = false
	var hasOnDupUpd = false
	var hasOrder = false


	fun executeQuery(db: Database): ResultSet {
		if (params == null)
			return db.executeQuery(toString())
		return db.executeQuery(toString(), *paramArray)
	}

	fun executeUpdate(db: Database): Int {
		if (params == null)
			return db.executeUpdate(toString())
		return db.executeUpdate(toString(), *paramArray)
	}

	fun executeUpdateWithAutoKeys(db: Database): ResultSet? {
		if (params == null)
			return db.executeUpdateWithAutoKeys(toString())
		return db.executeUpdateWithAutoKeys(toString(), *paramArray)
	}


	override fun toString() = sb.toString()


	fun p(param: Any?): QueryBuilder {
		params = (params ?: ArrayList<Any?>(1)).apply { add(param) }
		return this
	}


	fun select(col: String = "*"): QueryBuilder {
		if (col == "*")
			sb.append("SELECT *")
		else
			sb.append("SELECT ").appendIdentifier(col)

		return this
	}
	fun select(vararg cols: String): QueryBuilder {
		sb.append("SELECT ")
		cols.forEachIndexed { i,col ->
			if (i > 0) sb.append(',')
			sb.appendIdentifier(col)
		}
		return this
	}

	fun insert(table: String): QueryBuilder {
		sb.append("INSERT INTO ").appendIdentifier(table)
		return this
	}

	fun update(table: String): QueryBuilder {
		sb.append("UPDATE ").appendIdentifier(table)
		return this
	}

	fun delete(): QueryBuilder {
		sb.append("DELETE")
		return this
	}


	fun from(table: String): QueryBuilder {
		sb.append(" FROM ").appendIdentifier(table)
		return this
	}


	fun where(col: String, value: Any) = where0(col, '=', value)
	fun wherep(col: String, param: Any) = where0(col, "=?").p(param)

	fun whereNot(col: String, value: Any) = where0(col, "<>", value)
	fun wherepNot(col: String, param: Any) = where0(col, "<>?").p(param)

	fun whereNull(col: String): QueryBuilder {
		where0(col)
		sb.append(" IS NULL")
		return this
	}
	fun whereNotNull(col: String): QueryBuilder {
		where0(col)
		sb.append(" IS NOT NULL")
		return this
	}

	fun whereGt(col: String, value: Any) = where0(col, '>', value)
	fun wherepGt(col: String, param: Any) = where0(col, ">?").p(param)

	fun whereLt(col: String, value: Any) = where0(col, '<', value)
	fun wherepLt(col: String, param: Any) = where0(col, "<?").p(param)

	fun whereBetween(col: String, min: Any, max: Any): QueryBuilder {
		where0(col)
		sb.append(" BETWEEN ").appendLiteral(min).append(" AND ").appendLiteral(max)
		return this
	}
	fun wherepBetween(col: String, min: Any, max: Any): QueryBuilder {
		return where0(col, " BETWEEN ? AND ?").p(min).p(max)
	}

	fun whereNotBetween(col: String, min: Any, max: Any): QueryBuilder {
		where0(col)
		sb.append(" NOT BETWEEN ").appendLiteral(min).append(" AND ").appendLiteral(max)
		return this
	}
	fun wherepNotBetween(col: String, min: Any, max: Any): QueryBuilder {
		return where0(col, " NOT BETWEEN ? AND ?").p(min).p(max)
	}

	fun whereIn(col: String, values: Any): QueryBuilder {
		where0(col)
		sb.append(" IN (").appendJoined(values).append(')')
		return this
	}
	fun whereNotIn(col: String, values: Any): QueryBuilder {
		where0(col)
		sb.append(" NOT IN (").appendJoined(values).append(')')
		return this
	}

	private fun where0(col: String, eq: Any? = null, value: Any? = null): QueryBuilder {
		if (!hasWhere) {
			sb.append(" WHERE ")
			hasWhere = true
		}
		else sb.append(" AND ")

		sb.appendIdentifier(col)

		when (eq) {
			is Char -> sb.append(eq)
			is String -> sb.append(eq)
		}

		if (value != null)
			sb.appendLiteral(value)

		return this
	}


	fun orderBy(col: String, desc: Boolean = false): QueryBuilder {
		if (!hasOrder) {
			sb.append(" ORDER BY ")
			hasOrder = true
		}
		else sb.append(',')

		sb.appendIdentifier(col)

		if (desc)
			sb.append(" DESC")

		return this
	}


	fun limit(num: Int): QueryBuilder {
		sb.append(" LIMIT ").append(num)
		return this
	}


	fun set(col: String, value: Any?): QueryBuilder {
		return when (value) {
			null -> set0(col, "=NULL")
			else -> set0(col, '=', value)
		}
	}
	fun setp(col: String, value: Any?): QueryBuilder {
		return when (value) {
			null -> set(col, null)
			else -> set0(col, "=?").p(value)
		}
	}

	private fun set0(col: String, eq: Any, value: Any? = null): QueryBuilder {
		appendColValPair(col, eq, value, if (hasSet) ", " else " SET ")
		hasSet = true
		return this
	}


	fun onDupUpdate(col: String, value: Any?): QueryBuilder {
		return when (value) {
			null -> onDupUpdate0(col, "=NULL")
			else -> onDupUpdate0(col, '=', value)
		}
	}
	fun onDupUpdatep(col: String, value: Any?): QueryBuilder {
		return when (value) {
			null -> onDupUpdate(col, value)
			else -> onDupUpdate0(col, "=?").p(value)
		}
	}

	private fun onDupUpdate0(col: String, eq: Any, value: Any? = null): QueryBuilder {
		appendColValPair(col, eq, value, if (hasOnDupUpd) ", " else " ON DUPLICATE KEY UPDATE ")
		hasOnDupUpd = true
		return this
	}


	private fun appendColValPair(col: String, eq: Any, value: Any? = null, prefix: Any): QueryBuilder {
		when (prefix) {
			is Char -> sb.append(prefix)
			is String -> sb.append(prefix)
		}

		sb.appendIdentifier(col)

		when (eq) {
			is Char -> sb.append(eq)
			is String -> sb.append(eq)
		}

		if (value != null)
			sb.appendLiteral(value)

		return this
	}


	private fun StringBuilder.appendIdentifier(name: String): StringBuilder {
		if (quoteIdentifiers) append('`')
		append(name.escapeSqlIdentifier())
		if (quoteIdentifiers) append('`')
		return this
	}

	private fun StringBuilder.appendLiteral(value: Any): StringBuilder {
		// Common types (to avoid "toString" inside StringBuilder)
		return when (value) {
			is Boolean -> append(if (value) 1 else 0)

			is Long -> append(value)
			is Int -> append(value)
			is Short -> append(value)
			is Byte -> append(value)
			is Double -> append(value)
			is Float -> append(value)

			is String -> append('\'').append(value.escapeSqlLiteral()).append('\'')

			is Char -> append('\'').apply {
				val esc = value.escapeSqlLiteral()
				if (esc != null) append(esc)
				else append(value)
			}.append('\'')

			else -> appendLiteral(value.toString())
		}
	}

	private fun StringBuilder.appendJoined(values: Any): StringBuilder {
		var i = -1
		when (values) {
			is Array<*> -> values.forEach {
				if (it != null) {
					if (++i > 0) sb.append(',')
					appendLiteral(it)
				}
			}
			is Iterable<*> -> values.forEach {
				if (it != null) {
					if (++i > 0) sb.append(',')
					appendLiteral(it)
				}
			}
			else -> appendLiteral(values)
		}
		return this
	}
}
