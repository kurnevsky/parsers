package net.kurnevsky.parsers

import scala.util.parsing.combinator.RegexParsers

object ScalaParserCombinatorsExpr extends App with RegexParsers {
  lazy val natural: Parser[Int] =
    """\d+""".r ^^ { _.toInt }
  lazy val multdiv: Parser[Expr] =
    brackets ~ (elem('*') | '/') ~ multdiv ^^ {
      case l ~ o ~ r => Op(o, l, r)
    } | brackets
  lazy val addsub: Parser[Expr] =
    multdiv ~ (elem('+') | '-') ~ addsub ^^ {
      case l ~ o ~ r => Op(o, l, r)
    } | multdiv
  lazy val brackets: Parser[Expr] =
    '(' ~> addsub <~ ')' | natural ^^ Num
  lazy val expr: Parser[Expr] =
    addsub

  println(parseAll(expr, "5+3-7"))
}
