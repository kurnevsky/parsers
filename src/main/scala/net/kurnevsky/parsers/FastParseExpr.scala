package net.kurnevsky.parsers

import fastparse._, NoWhitespace._

object FastParseExpr extends App {
  def natural[_: P]: P[Int] =
    P(CharIn("0-9").rep(1).!.map(_.toInt))

  def multdiv[_: P]: P[Expr] =
    (brackets ~ (P("*").map(_ => '*') | P("/").map(_ => '/')) ~ multdiv).map {
      case (l, o, r) => Op(o, l, r)
    } | brackets
  def addsub[_: P]: P[Expr] =
    (multdiv ~ (P("+").map(_ => '+') | P("-").map(_ => '-')) ~ addsub).map {
      case (l, o, r) => Op(o, l, r)
    } | multdiv
  def brackets[_: P]: P[Expr] =
    P(("(" ~/ addsub ~ ")") | natural.map(Num))
  def expr[_: P]: P[Expr] =
    addsub ~ End

  println(parse("5+3-7", expr(_)))
}
