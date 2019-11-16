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
---

# Определение парсера

Парсер - это функция, принимающая на вход строку и возвращающая какое-либо значение (обычно синтаксическое дерево):

```scala
trait Parser[B] extends (String => B)
```

. . .

Можно обобщить на произвольную последовательность:

```scala
trait Parser[A, B] extends (Seq[A] => B)
```

. . .

Необходимо возвращать неразобранную часть строки:

```scala
trait Parser[A, B] extends (Seq[A] => (Seq[A], B))
```

. . .

Строка может быть разобрана множеством способов:

```scala
trait Parser[A, B] extends (Seq[A] => LazyList[(Seq[A], B)])
```

# Простейшие парсеры

Парсер, распознающий символ по условию:

```scala
def satisfy[A](f: A => Boolean): Parser[A, A] = new Parser[A, A] {
  override def apply(seq: Seq[A]): LazyList[(Seq[A], A)] =
    seq match {
      case h :: t if f(h) => LazyList(t -> h)
      case _ => LazyList.empty
    }
}
```

. . .

Парсер, распознающий конкретный символ:

```scala
implicit def symbol[A](c: A): Parser[A, A] = satisfy(_ == c)
```

# Простейшие парсеры

Парсер, распознающий последовательность символов:

```scala
implicit def token[A](k: Seq[A]): Parser[A, Seq[A]] = new Parser[A, Seq[A]] {
  override def apply(seq: Seq[A]): LazyList[(Seq[A], Seq[A])] =
    if (k == seq.take(k.size))
      LazyList(seq.drop(k.size) -> k)
    else
      LazyList.empty
}
```

# Простейшие парсеры

Парсер, не принимающий ничего на вход, но всегда возвращающий данное значение:

```scala
def succeed[A, B](v: B): Parser[A, B] = new Parser[A, B] {
  override def apply(seq: Seq[A]): LazyList[(Seq[A], B)] =
    LazyList(seq -> v)
}
```

# Простейшие парсеры

Парсер, который не распознаёт ни один символ входной строки:

```scala
def fail[A]: Parser[A, Nothing] = new Parser[A, Nothing] {
  override def apply(seq: Seq[A]): LazyList[(Seq[A], Nothing)] =
    LazyList.empty
}
```

# Комбинаторы парсеров

Комбинатор тождественного отображения:

```scala
trait Parser[A, B] extends (Seq[A] => LazyList[(Seq[A], B)]) { self =>
  def p: Parser[A, B] = self
}
```

# Комбинаторы парсеров

Комбинатор отображения:

```scala
trait Parser[A, B] extends (Seq[A] => LazyList[(Seq[A], B)]) { self =>
  def map[C](f: B => C): Parser[A, C] = new Parser[A, C] {
    override def apply(seq: Seq[A]): LazyList[(Seq[A], C)] =
      self(seq).map {
        case (tail, b) => tail -> f(b)
      }
  }
}
```

. . .

Синоним:

```scala
trait Parser[A, B] extends (Seq[A] => LazyList[(Seq[A], B)]) { self =>
  def ^^[C](f: B => C): Parser[A, C] =
    map(f)
}
```

# Комбинаторы парсеров

Комбинатор монадического связывания:

```scala
trait Parser[A, B] extends (Seq[A] => LazyList[(Seq[A], B)]) { self =>
  def flatMap[C](f: B => Parser[A, C]): Parser[A, C] = new Parser[A, C] {
    override def apply(seq: Seq[A]): LazyList[(Seq[A], C)] =
      for {
        (tail1, b) <- self(seq)
        (tail2, c) <- f(b)(tail1)
      } yield tail2 -> c
  }
}
```

# Комбинаторы парсеров

Вспомагательный класс для объединения результатов последовательно примененных парсеров:

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

Комбинатор последовательного соединения парсеров:

```scala
trait Parser[A, B] extends (Seq[A] => LazyList[(Seq[A], B)]) { self =>
  def ~[C](next: => Parser[A, C]): Parser[A, B ~ C] = new Parser[A, B ~ C] {
    override def apply(seq: Seq[A]): LazyList[(Seq[A], B ~ C)] =
      for {
        (tail1, b) <- self(seq)
        (tail2, c) <- next(tail1)
      } yield tail2 -> new ~(b, c)
  }
}
```

# Комбинаторы парсеров

С игнорированием левого результата:

```scala
trait Parser[A, B] extends (Seq[A] => LazyList[(Seq[A], B)]) { self =>
  def ~>[C](next: => Parser[A, C]): Parser[A, C] =
    self ~ next ^^ { case _ ~ c => c }
}
```

. . .

С игнорированием правого результата:

```scala
trait Parser[A, B] extends (Seq[A] => LazyList[(Seq[A], B)]) { self =>
  def <~[C](next: => Parser[A, C]): Parser[A, B] =
    self ~ next ^^ { case b ~ _ => b }
}
```

# Комбинаторы парсеров

Комбинатор параллельного соединения парсеров:

```scala
trait Parser[A, B] extends (Seq[A] => LazyList[(Seq[A], B)]) { self =>
  def |(other: => Parser[A, B]): Parser[A, B] = new Parser[A, B] {
    override def apply(seq: Seq[A]): LazyList[(Seq[A], B)] =
      self(seq) ++ other(seq)
  }
}
```

# Комбинаторы парсеров

Комбинатор, гарантирующий завершение разбираемой строки:

```scala
trait Parser[A, B] extends (Seq[A] => LazyList[(Seq[A], B)]) { self =>
  def just: Parser[A, B] = new Parser[A, B] {
    override def apply(seq: Seq[A]): LazyList[(Seq[A], B)] =
      self(seq).filter { case (tail, _) => tail.isEmpty }
  }
}
```

# Комбинаторы парсеров

Комбинаторы повторения:

```scala
trait Parser[A, B] extends (Seq[A] => LazyList[(Seq[A], B)]) { self =>
  def rep: Parser[A, List[B]] =
    self ~ self.rep ^^ { case h ~ t => h :: t } | succeed(Nil)
}
```

. . .

```scala
trait Parser[A, B] extends (Seq[A] => LazyList[(Seq[A], B)]) { self =>
  def rep1: Parser[A, List[B]] =
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
lazy val digit: Parser[Char, Int] =
  ('0'.p | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9') ^^ { _ - '0' }
```

. . .

```scala
lazy val natural: Parser[Char, Int] =
  digit.rep1 ^^ { _.foldLeft(0)(_ * 10 + _) }
```

# Пример разбора арифметических выражений

```scala
lazy val multdiv: Parser[Char, Expr] =
  brackets ~ ('*'.p | '/') ~ multdiv ^^ {
    case l ~ o ~ r => Op(o, l, r): Expr
  } | brackets
```

. . .

```scala
lazy val addsub: Parser[Char, Expr] =
  multdiv ~ ('+'.p | '-') ~ addsub ^^ {
    case l ~ o ~ r => Op(o, l, r): Expr
  } | multdiv
```

. . .

```scala
lazy val brackets: Parser[Char, Expr] =
  '(' ~> addsub <~ ')' | natural ^^ Num
```

. . .

```scala
lazy val expr: Parser[Char, Expr] =
  addsub.just
```

# Пример разбора арифметических выражений

```
> 5+3-7
```

\ 

. . .

```
Op(+,Num(5),Op(-,Num(3),Num(7)))
```

\ 

. . .

\begin{center}
\begin{BVerbatim}
  +
 / \
5   -
   / \
  3   7
\end{BVerbatim}
\end{center}

# Пример разбора арифметических выражений

```scala
lazy val multdiv: Parser[Char, Expr] =
  multdiv ~ ('*'.p | '/') ~ brackets ^^ {
    case l ~ o ~ r => Op(o, l, r): Expr
  } | brackets
lazy val addsub: Parser[Char, Expr] =
  addsub ~ ('+'.p | '-') ~ multdiv ^^ {
    case l ~ o ~ r => Op(o, l, r): Expr
  } | multdiv
lazy val brackets: Parser[Char, Expr] =
  '(' ~> addsub <~ ')' | natural ^^ Num
lazy val expr: Parser[Char, Expr] =
  addsub.just
```

# Пример разбора арифметических выражений

```
> 5+3-7
```

```
Exception in thread "main" java.lang.StackOverflowError
        at Arithmetic0.addsub$(Parsers.scala:114)
        at Main$.addsub$lzycompute(Parsers.scala:155)
        at Main$.addsub(Parsers.scala:155)
        at Arithmetic0.addsub(Parsers.scala:112)
        at Arithmetic0.addsub$(Parsers.scala:114)
        at Main$.addsub$lzycompute(Parsers.scala:155)
        at Main$.addsub(Parsers.scala:155)
        at Arithmetic0.addsub(Parsers.scala:112)
        at Arithmetic0.addsub$(Parsers.scala:114)
        at Main$.addsub$lzycompute(Parsers.scala:155)
        at Main$.addsub(Parsers.scala:155)
        at Arithmetic0.addsub(Parsers.scala:112)
        at Arithmetic0.addsub$(Parsers.scala:114)
        at Main$.addsub$lzycompute(Parsers.scala:155)
        at Main$.addsub(Parsers.scala:155)
        at Arithmetic0.addsub(Parsers.scala:112)
        at Arithmetic0.addsub$(Parsers.scala:114)
        at Main$.addsub$lzycompute(Parsers.scala:155)
        at Main$.addsub(Parsers.scala:155)
        at Arithmetic0.addsub(Parsers.scala:112)
        at Arithmetic0.addsub$(Parsers.scala:114)
        at Main$.addsub$lzycompute(Parsers.scala:155)
        at Main$.addsub(Parsers.scala:155)
        at Arithmetic0.addsub(Parsers.scala:112)
        at Arithmetic0.addsub$(Parsers.scala:114)
        at Main$.addsub$lzycompute(Parsers.scala:155)
        at Main$.addsub(Parsers.scala:155)
        at Arithmetic0.addsub(Parsers.scala:112)
        at Arithmetic0.addsub$(Parsers.scala:114)
        at Main$.addsub$lzycompute(Parsers.scala:155)
        at Main$.addsub(Parsers.scala:155)
        at Arithmetic0.addsub(Parsers.scala:112)
        at Arithmetic0.addsub$(Parsers.scala:114)
        at Main$.addsub$lzycompute(Parsers.scala:155)
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
lazy val multdiv: Parser[Char, Expr] =
  brackets ~ (('*'.p | '/') ~ brackets).rep ^^ {
    case e ~ l => l.foldLeft(e) { (acc, nxt) => nxt match {
      case o ~ x => Op(o, acc, x)
    } }
  }
lazy val addsub: Parser[Char, Expr] =
  multdiv ~ (('+'.p | '-') ~ multdiv).rep ^^ {
    case e ~ l => l.foldLeft(e) { (acc, nxt) => nxt match {
      case o ~ x => Op(o, acc, x)
    } }
  }
lazy val brackets: Parser[Char, Expr] =
  '(' ~> addsub <~ ')' | natural ^^ Num
lazy val expr: Parser[Char, Expr] =
  addsub.just
```

# Пример разбора арифметических выражений

```
> 5+3-7
```

\ 

```
Op(-,Op(+,Num(5),Num(3)),Num(7))
```

\ 

\begin{center}
\begin{BVerbatim}
    -
   / \
  +   7
 / \
5   3
\end{BVerbatim}
\end{center}

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
