
import $ivy.`com.lihaoyi::requests:0.6.5`
import $ivy.`com.lihaoyi::upickle:0.9.5`

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

@main
def main(month: Int, year: Int): Unit = {

  println(s"Running for month: $month and year: $year")

  val fromDateTime: LocalDateTime = LocalDate.of(year, month, 1).atStartOfDay() // Starts at 1
  val toDateTime: LocalDateTime = LocalDate.of(year, month, 1).`with`(TemporalAdjusters.lastDayOfMonth()).atTime(23, 59, 59)

  println(s"Date interval: from $fromDateTime to $toDateTime")

  val splitwiseApiKey = sys.env.getOrElse("SPLITWISE_API_KEY", throw new RuntimeException("SPLITWISE_API_KEY not found"))
  val authHeader = Map("Authorization" -> s"Bearer $splitwiseApiKey")

  val group = sys.env.getOrElse("SPLITWISE_GROUP", throw new RuntimeException("SPLITWISE_GROUP not found"))

  val user = requests.get("https://www.splitwise.com/api/v3.0/get_current_user", headers = authHeader)
  val userId = ujson.read(user.text)("user")("id").num.toInt
  println(s"UserId: $userId")

  // Setting the limit to 0 means unlimited requests (up to a maximum we won't probably get)
  // Visible=true is an undocumented flag, but it's what the actual application uses so...
  val expensesResponse = requests.get(
    s"https://secure.splitwise.com/api/v3.0/get_expenses?" +
      s"group_id=$group&" +
      s"dated_after=$fromDateTime&" +
      s"dated_before=$toDateTime&" +
      s"visible=true&" +
      s"limit=0",
    headers = authHeader
  )
  val expensesJson = ujson.read(expensesResponse.text)


  /*
   * All the expenses that are created by you => add them as liabilities
   * For every expense, for every repayment
   *    => if you are the 'from', add the repayment amount as liability
   *    => if you are the 'to', subtract the repayment amount as liability
   */

  var liabilities = 0.0d
  expensesJson("expenses").arr.foreach { expense =>
    val creatorId = expense("created_by")("id").num.toInt
    val description = expense("description").str
    val cost = expense("cost").str.toDouble
    val isPayment = expense("payment").bool

    if (!isPayment) {
      if (creatorId == userId) {
        println(s"+ $cost for '$description', because we created it")
        liabilities += cost
      }

      expense("repayments").arr.foreach { repayment =>
        val toId = repayment("to").num.toInt
        val fromId = repayment("from").num.toInt
        val amount = repayment("amount").str.toDouble

        if (toId == userId) {
          println(s"- $amount for '$description', as a repayment we are owed")
          liabilities -= amount
        } else if (fromId == userId) {
          println(s"+ $amount for '$description', as a repayment we owe")
          liabilities += amount
        }
      }
    }
  }

  println("----------------")
  println(s"Final liabilities for month: $month and year: $year are: $liabilities")
}
