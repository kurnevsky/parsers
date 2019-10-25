package net.kurnevsky.parsers

sealed trait Expr
final case class Num(n: Int) extends Expr
final case class Op(o: Char, l: Expr, r: Expr) extends Expr
