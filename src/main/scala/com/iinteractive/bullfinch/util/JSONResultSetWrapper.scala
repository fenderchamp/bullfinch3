package com.iinteractive.bullfinch.util

import com.codahale.logula.Logging
import java.sql.{ResultSet,SQLException,Types}
import com.codahale.jerkson.Json._

case class Column(
  index: Int,
  label: String,
  ctype: Int
)

class JSONResultSetWrapper(resultSet: ResultSet) extends Iterator[String] with Logging {

	// Use this as a sentinel to determine if we've already called next(), this
	// way we can peek ahead in the hasNext.
	var checkedNext: Boolean = false;
	var hasNextFlag: Boolean = false;

  val columns = {
    val md = resultSet.getMetaData
    // +1 since we're doing things 1 based, not 0
    1 to (md.getColumnCount) map { i =>
      Column(
        index = i,
        label = md.getColumnLabel(i),
        ctype = md.getColumnType(i)
      )
    }
  }
  
  override def hasNext: Boolean = {

		// Don't advance the resultset unless next() has cleared the checkedNext
		// sentinel.  This makes it save to call hasNext multiple times so long
		// as next isn't called.
    if(!checkedNext) {
      try {
        hasNextFlag = resultSet.next()
        checkedNext = true
      } catch {
				// We'll complain, but otherwise we'll return a false, can't do
				// much about it here.
        case e: Exception => {
          log.error("Failed to call next on ResultSet: ", e)
          hasNextFlag = false
        }
      }
    }
    
    hasNextFlag
  }
  
  override def next(): String = {
    
    var obj = scala.collection.mutable.Map[String,Any]()
    
    obj += ("row_num" -> resultSet.getRow)
    
    columns.foreach { col =>
      col.ctype match {
        case Types.CHAR | Types.VARCHAR | Types.LONGVARCHAR => obj += (col.label -> resultSet.getString(col.index))
        case Types.NUMERIC | Types.DECIMAL                  => obj += (col.label -> resultSet.getBigDecimal(col.index))
        case Types.BIT | Types.BOOLEAN                      => obj += (col.label -> resultSet.getBoolean(col.index))
        case Types.TINYINT | Types.SMALLINT | Types.INTEGER => obj += (col.label -> resultSet.getInt(col.index))
        case Types.BIGINT                                   => obj += (col.label -> resultSet.getLong(col.index))
        case Types.REAL | Types.FLOAT                       => obj += (col.label -> resultSet.getFloat(col.index))
        case Types.DOUBLE                                   => obj += (col.label -> resultSet.getDouble(col.index))
        case Types.TIMESTAMP                                => obj += (col.label -> resultSet.getString(col.index))
        case Types.DATE                                     => {
          val res = resultSet.getDate(col.index)
          val str = res match {
            case null => null
            case _ => res.toString
          }
          obj += (col.label -> str)
        }
        case Types.TIME                                     => {
          val res = resultSet.getTime(col.index)
          val str = res match {
            case null => null
            case _ => res.toString
          }
          obj += (col.label -> str)
        }
        case _ => throw new SQLException("Unrecognized type for column " + col.label + ": " + col.ctype)
      }
    }
    
    // Reset the sentinel so that the next hasNext will work
    checkedNext = false
    
    generate(obj)
  }
}