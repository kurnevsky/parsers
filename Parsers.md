---
title: Parser Combinators
subtitle: The functional approach to parsing
author: Evgeny Kurnevsky
date: 2019
theme: Madrid
colortheme: default
fontfamily: noto-sans
aspectratio: 169
fontsize: 10pt
header-includes: |
  \usepackage{tikz}
---

# Задача

Разобрать выражение `7+5-3*2` в дерево:

\begin{center}
  \begin{tikzpicture}[
      every node/.style = {shape=circle, draw,
      align=center, minimum size=2em},
      level 1/.style = {sibling distance=11em},
      level 2/.style = {sibling distance=7em}]
    \node {-}
      child { node {+}
        child { node {7} }
        child { node {5} }
      }
      child { node {*}
        child { node {3} }
        child { node {2} }
      };
  \end{tikzpicture}
\end{center}

# antlr

```antlr
grammar Expr;
expr: expr ('*'|'/') expr
    | expr ('+'|'-') expr
    | INT
    | '(' expr ')'
    ;
INT: [0-9]+ ;
```

# antlr

```
> antlr4 -visitor Expr.g4

ExprBaseListener.java
ExprBaseVisitor.java
ExprLexer.java
ExprListener.java
ExprParser.java
ExprVisitor.java
```

# antlr

## Listener

```java
public interface ExprListener extends ParseTreeListener {
	void enterExpr(ExprParser.ExprContext ctx);
	void exitExpr(ExprParser.ExprContext ctx);
}
```

## Visitor

```java
public interface ExprVisitor<T> extends ParseTreeVisitor<T> {
	T visitExpr(ExprParser.ExprContext ctx);
}
```

# Определение парсера

Парсер - это функция, принимающая на вход строку и возвращающая значение:

```scala
trait Parser[T] extends (String => T)
```

. . .

Необходимо возвращать неразобранную часть строки:

```scala
trait Parser[T] extends (String => (String, T))
```

. . .

Строка может быть разобрана множеством способов:

```scala
trait Parser[T] extends (String => LazyList[(String, T)])
```

# Простейшие парсеры

## Парсер, распознающий символ по условию

```scala
def satisfy(f: Char => Boolean): Parser[Char] = new Parser[Char] {
  override def apply(s: String): LazyList[(String, Char)] =
    s.headOption match {
      case Some(c) if f(c) => LazyList(s.tail -> c)
      case _ => LazyList.empty
    }
}
```

. . .

## Парсер, распознающий конкретный символ

```scala
implicit def symbol(c: Char): Parser[Char] = satisfy(_ == c)
```

# Простейшие парсеры

## Парсер, распознающий последовательность символов

```scala
implicit def token(t: String): Parser[String] = new Parser[String] {
  override def apply(s: String): LazyList[(String, String)] =
    if (t == s.take(t.size))
      LazyList(s.drop(t.size) -> t)
    else
      LazyList.empty
}
```

# Простейшие парсеры

## Парсер, всегда возвращающий данное значение

```scala
def succeed[T](v: T): Parser[T] = new Parser[T] {
  override def apply(s: String): LazyList[(String, T)] =
    LazyList(s -> v)
}
```

# Простейшие парсеры

## Парсер, который не распознаёт ни один символ входной строки

```scala
def fail: Parser[Nothing] = new Parser[Nothing] {
  override def apply(s: String): LazyList[(String, Nothing)] =
    LazyList.empty
}
```

# Комбинаторы парсеров

## Комбинатор тождественного отображения

```scala
trait Parser[T] extends (String => LazyList[(String, T)]) { self =>
  def p: Parser[T] = self
}
```

# Комбинаторы парсеров

## Комбинатор отображения

```scala
trait Parser[T] extends (String => LazyList[(String, T)]) { self =>
  def map[U](f: T => U): Parser[U] = new Parser[U] {
    override def apply(s: String): LazyList[(String, U)] =
      self(s).map {
        case (tail, b) => tail -> f(b)
      }
  }
}
```

. . .

## Синоним

```scala
trait Parser[T] extends (String => LazyList[(String, T)]) { self =>
  def ^^[U](f: T => U): Parser[U] =
    map(f)
}
```

# Комбинаторы парсеров

## Комбинатор монадического связывания

```scala
trait Parser[T] extends (String => LazyList[(String, T)]) { self =>
  def flatMap[U](f: T => Parser[U]): Parser[U] = new Parser[U] {
    override def apply(s: String): LazyList[(String, U)] =
      for {
        (tail1, b) <- self(s)
        (tail2, c) <- f(b)(tail1)
      } yield tail2 -> c
  }
}
```

# Комбинаторы парсеров

Класс для объединения результатов последовательно примененных парсеров:

```scala
case class ~[+A, +B](_1: A, _2: B)
```

. . .

```
~[A, B] <=> A ~ B
```

. . .

```
~[~[A, B], C] <=> A ~ B ~ C
```

# Комбинаторы парсеров

## Комбинатор последовательного соединения парсеров

```scala
trait Parser[T] extends (String => LazyList[(String, T)]) { self =>
  def ~[U](next: => Parser[U]): Parser[T ~ U] = new Parser[T ~ U] {
    override def apply(s: String): LazyList[(String, T ~ U)] =
      for {
        (tail1, b) <- self(s)
        (tail2, c) <- next(tail1)
      } yield tail2 -> new ~(b, c)
  }
}
```

# Комбинаторы парсеров

## С игнорированием левого результата

```scala
trait Parser[T] extends (String => LazyList[(String, T)]) { self =>
  def ~>[U](next: => Parser[U]): Parser[U] =
    self ~ next ^^ { case _ ~ c => c }
}
```

## С игнорированием правого результата

```scala
trait Parser[T] extends (String => LazyList[(String, T)]) { self =>
  def <~[U](next: => Parser[U]): Parser[T] =
    self ~ next ^^ { case b ~ _ => b }
}
```

# Комбинаторы парсеров

## Комбинатор параллельного соединения парсеров

```scala
trait Parser[T] extends (String => LazyList[(String, T)]) { self =>
  def |(other: => Parser[T]): Parser[T] = new Parser[T] {
    override def apply(s: String): LazyList[(String, T)] =
      self(s) ++ other(s)
  }
}
```

# Комбинаторы парсеров

## Комбинатор, гарантирующий завершение разбираемой строки

```scala
trait Parser[T] extends (String => LazyList[(String, T)]) { self =>
  def just: Parser[T] = new Parser[T] {
    override def apply(s: String): LazyList[(String, T)] =
      self(s).filter { case (tail, _) => tail.isEmpty }
  }
}
```

# Комбинаторы парсеров

## Комбинатор повторения 0 или более раз

```scala
trait Parser[T] extends (String => LazyList[(String, T)]) { self =>
  def rep: Parser[List[T]] =
    self ~ self.rep ^^ { case h ~ t => h :: t } | succeed(Nil)
}
```

. . .

## Комбинатор повторения 1 или более раз

```scala
trait Parser[T] extends (String => LazyList[(String, T)]) { self =>
  def rep1: Parser[List[T]] =
    self ~ self.rep ^^ { case h ~ t => h :: t }
}
```

# Пример разбора арифметических выражений

```scala
sealed trait Expr
final case class Num(n: Int) extends Expr
final case class Op(o: Char, l: Expr, r: Expr) extends Expr
```

# Пример разбора арифметических выражений

```scala
lazy val digit: Parser[Int] =
  ('0'.p | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9') ^^ { _ - '0' }
```

. . .

```scala
lazy val natural: Parser[Int] =
  digit.rep1 ^^ { _.foldLeft(0)(_ * 10 + _) }
```

# Пример разбора арифметических выражений

```scala
lazy val multdiv: Parser[Expr] =
  brackets ~ ('*'.p | '/') ~ multdiv ^^ {
    case l ~ o ~ r => Op(o, l, r): Expr
  } | brackets
```

. . .

```scala
lazy val addsub: Parser[Expr] =
  multdiv ~ ('+'.p | '-') ~ addsub ^^ {
    case l ~ o ~ r => Op(o, l, r): Expr
  } | multdiv
```

. . .

```scala
lazy val brackets: Parser[Expr] =
  '(' ~> addsub <~ ')' | natural ^^ Num
```

. . .

```scala
lazy val expr: Parser[Expr] =
  addsub.just
```

# Пример разбора арифметических выражений

```
> 7+5-3*2
```

. . .

```
Op(+,Num(7),Op(-,Num(5),Op(*,Num(3),Num(2))))
```

. . .

\begin{center}
  \begin{tikzpicture}[sibling distance=7em,
      every node/.style = {shape=circle,
      draw, align=center, minimum size=2em}]
    \node {+}
      child { node {7} }
      child { node {-}
        child { node {5} }
        child { node {*}
          child { node {3} }
          child { node {2} }
        }
      };
  \end{tikzpicture}
\end{center}

# Пример разбора арифметических выражений

```scala
lazy val multdiv: Parser[Expr] =
  multdiv ~ ('*'.p | '/') ~ brackets ^^ {
    case l ~ o ~ r => Op(o, l, r): Expr
  } | brackets
lazy val addsub: Parser[Expr] =
  addsub ~ ('+'.p | '-') ~ multdiv ^^ {
    case l ~ o ~ r => Op(o, l, r): Expr
  } | multdiv
lazy val brackets: Parser[Expr] =
  '(' ~> addsub <~ ')' | natural ^^ Num
lazy val expr: Parser[Expr] =
  addsub.just
```

# Пример разбора арифметических выражений

```
> 7+5-3*2
```

```
Exception in thread "main" java.lang.StackOverflowError
        at ArithmeticLeftRecursive.addsub(Parsers.scala:112)
        at ArithmeticLeftRecursive.addsub$(Parsers.scala:114)
        at Main$.addsub$lzycompute(Parsers.scala:174)
        at Main$.addsub(Parsers.scala:174)
        at ArithmeticLeftRecursive.addsub(Parsers.scala:112)
        at ArithmeticLeftRecursive.addsub$(Parsers.scala:114)
        at Main$.addsub$lzycompute(Parsers.scala:174)
        at Main$.addsub(Parsers.scala:174)
        at ArithmeticLeftRecursive.addsub(Parsers.scala:112)
        at ArithmeticLeftRecursive.addsub$(Parsers.scala:114)
        at Main$.addsub$lzycompute(Parsers.scala:174)
        at Main$.addsub(Parsers.scala:174)
        at ArithmeticLeftRecursive.addsub(Parsers.scala:112)
        at ArithmeticLeftRecursive.addsub$(Parsers.scala:114)
        at Main$.addsub$lzycompute(Parsers.scala:174)
        at Main$.addsub(Parsers.scala:174)
        at ArithmeticLeftRecursive.addsub(Parsers.scala:112)
        at ArithmeticLeftRecursive.addsub$(Parsers.scala:114)
        at Main$.addsub$lzycompute(Parsers.scala:174)
        at Main$.addsub(Parsers.scala:174)
        at ArithmeticLeftRecursive.addsub(Parsers.scala:112)
        at ArithmeticLeftRecursive.addsub$(Parsers.scala:114)
        at Main$.addsub$lzycompute(Parsers.scala:174)
        at Main$.addsub(Parsers.scala:174)
        at ArithmeticLeftRecursive.addsub(Parsers.scala:112)
        at ArithmeticLeftRecursive.addsub$(Parsers.scala:114)
        at Main$.addsub$lzycompute(Parsers.scala:174)
        at Main$.addsub(Parsers.scala:174)
```

# Левая рекурсия!

```
A  -> Aa|b
```

\ 

. . .

```
A  -> bA'
A' -> aA'|e
```

\ 

. . .

```
A  -> ba*
```

# Пример разбора арифметических выражений

```scala
lazy val multdiv: Parser[Expr] =
  brackets ~ (('*'.p | '/') ~ brackets).rep ^^ {
    case e ~ l => l.foldLeft(e) { (acc, nxt) => nxt match {
      case o ~ x => Op(o, acc, x)
    } }
  }
lazy val addsub: Parser[Expr] =
  multdiv ~ (('+'.p | '-') ~ multdiv).rep ^^ {
    case e ~ l => l.foldLeft(e) { (acc, nxt) => nxt match {
      case o ~ x => Op(o, acc, x)
    } }
  }
lazy val brackets: Parser[Expr] =
  '(' ~> addsub <~ ')' | natural ^^ Num
lazy val expr: Parser[Expr] =
  addsub.just
```

# Пример разбора арифметических выражений

```
> 7+5-3*2
```

```
Op(-,Op(+,Num(5),Num(3)),Num(7))
```

\begin{center}
  \begin{tikzpicture}[
      every node/.style = {shape=circle, draw,
      align=center, minimum size=2em},
      level 1/.style = {sibling distance=11em},
      level 2/.style = {sibling distance=7em}]
    \node {-}
      child { node {+}
        child { node {7} }
        child { node {5} }
      }
      child { node {*}
        child { node {3} }
        child { node {2} }
      };
  \end{tikzpicture}
\end{center}

# Реальный мир

```scala
trait Parser[A, B] extends (Seq[A] => LazyList[(Seq[A], B)])
```

. . .

```scala
case class Success[+T](result: T, next: Input) extends ParseResult[T]
case class Failure(msg: String, next: Input) extends ParseResult[T]
case class Error(msg: String, next: Input) extends ParseResult[T]
```

. . .

```scala
("a" ~ "b") | ("a" ~ "c")
```

```scala
("a" | "ab") ~ "b"
```

# Scala библиотеки

- scala-parser-combinators
- atto
- parboiled
- gll-combinators
- fastparse

# scala-parser-combinators

```scala
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
```

# atto

```scala
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
```

# parboiled

```scala
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
```

# gll-combinators

```scala
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
```

# fastparse

```scala
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
```
