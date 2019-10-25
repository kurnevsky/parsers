package net.kurnevsky.parsers

import shapeless._
import org.parboiled2._

object ParboiledExpr extends App {
  class ExprParser(val input: ParserInput) extends Parser {
    def natural: Rule1[Int] = rule {
      capture(oneOrMore(CharPredicate.Digit)) ~> { (_: String).toInt }
    }

    def multdiv: Rule1[Expr] = rule {
       brackets ~ push(cursorChar) ~ (ch('*') | '/') ~ multdiv ~> {
         (l, o, r) => Op(o, l, r)
       } | brackets
    }
    def addsub: Rule1[Expr] = rule {
      multdiv ~ push(cursorChar) ~ (ch('+') | '-') ~ addsub ~> {
        (l, o, r) => Op(o, l, r)
      } | multdiv
    }
    def brackets: Rule1[Expr] = rule {
      '(' ~ addsub ~ ')' | natural ~> Num
    }
    def expr: Rule1[Expr] = rule {
      addsub ~ EOI
    }
  }

  println(new ExprParser("5+3-7").expr.run())
}
