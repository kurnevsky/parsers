package net.kurnevsky.parsers

import atto._, Atto._
import cats.implicits._

object AttoExpr extends App {
  lazy val natural: Parser[Int] =
    many(digit) -| { _.foldLeft(0)(_ * 10 + _) }
  lazy val multdiv: Parser[Expr] =
    brackets ~ (char('*') | char('/')) ~ multdiv -| {
      case ((l, o), r) => Op(o, l, r)
    } | brackets
  lazy val addsub: Parser[Expr] =
    multdiv ~ (char('+') | char('-')) ~ addsub -| {
      case ((l, o), r) => Op(o, l, r)
    } | multdiv
  lazy val brackets: Parser[Expr] =
    char('(') ~> addsub <~ char(')') | natural -| Num
  lazy val expr: Parser[Expr] =
    addsub

  println(expr.parseOnly("5+3-7"))
}
