package net.kurnevsky.parsers

import com.codecommit.gll._

object GLLExpr extends App with RegexParsers {
  lazy val natural: Parser[Int] =
    """\d+""".r ^^ { _.toInt }

  lazy val multdiv: Parser[Expr] =
    brackets ~ ("*" | "/") ~ multdiv ^^ {
      case (l, o, r) => Op(o.head, l, r)
    } | brackets
  lazy val addsub: Parser[Expr] =
    multdiv ~ ("+" | "-") ~ addsub ^^ {
      case (l, o, r) => Op(o.head, l, r)
    } | multdiv
  lazy val brackets: Parser[Expr] =
    "(" ~> addsub <~ ")" | natural ^^ Num
  lazy val expr: Parser[Expr] =
    addsub

  println(expr("5+3-7"))
}
