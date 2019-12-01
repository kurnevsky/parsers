---
title: Парсер-комбинаторы
subtitle: Функциональный подход к парсингу
author: Евгений Курневский
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

Разобрать выражение `7-1-3*2` в дерево:

\begin{center}
  \begin{tikzpicture}[
    every node/.style = {
      shape = circle,
      draw,
      align = center,
      minimum size = 2em
    },
    level 1/.style = {
      sibling distance = 11em
    },
    level 2/.style = {
      sibling distance = 7em
    }
  ]
    \node {-}
      child { node {-}
        child { node {7} }
        child { node {1} }
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

# antlr

Минусы:

- Нужно знать язык описания грамматик
- Необходим механизм модключения парсера к проекту
- Нужно преобразовывать дерево в удобное для работы

Можно ли сделать парсинг проще?

# Определение парсера

Парсер - это функция, принимающая на вход строку и возвращающая значение:

```scala
trait Parser[+T] extends (String => T)
```

. . .

Необходимо возвращать неразобранную часть строки:

```scala
trait Parser[+T] extends (String => (String, T))
```

. . .

Строка может быть разобрана множеством способов:

```scala
trait Parser[+T] extends (String => LazyList[(String, T)])
```

# Простейшие парсеры

## Парсер, распознающий конкретный символ

```scala
implicit def symbol(c: Char): Parser[Char] = new Parser[Char] {
  override def apply(s: String): LazyList[(String, Char)] =
    s.headOption match {
      case Some(`c`) => LazyList(s.tail -> c)
      case _ => LazyList.empty
    }
}
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

Создание парсеров:

```scala
'a'.p
"abc".p
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

Парсер числа:

```scala
val string: Parser[String] = ???

string.map(_.toInt)
string ^^ { _.toInt }
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

Парсер четных чисел:

```scala
val number: Parser[Int] = ???

number.flatMap { n =>
  if (n % 2 == 0)
    succeed(n / 2)
  else
    fail
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

```
~[~[~[A, B], C], D] <=> A ~ B ~ C ~ D
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

Парсер двух чисел, разделенных запятой:

```scala
val number: Parser[Int] = ???

number ~ ',' ~ number ^^ {
  case n1 ~ _ ~ n2 => n1 -> n2
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

Парсер числа в скобках:

```scala
val number: Parser[Int] = ???

'(' ~> number <~ ')'
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

Парсер цифры:

```scala
'0'.p | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9'
```

# Комбинаторы парсеров

## Комбинатор повторения 0 или более раз

```scala
trait Parser[T] extends (String => LazyList[(String, T)]) { self =>
  def rep: Parser[List[T]] =
    self ~ self.rep ^^ { case h ~ t => h :: t } | succeed(Nil)
}
```

## Комбинатор повторения 1 или более раз

```scala
trait Parser[T] extends (String => LazyList[(String, T)]) { self =>
  def rep1: Parser[List[T]] =
    self ~ self.rep ^^ { case h ~ t => h :: t }
}
```

# Комбинаторы парсеров

Парсер списка чисел, разделенных запятой:

```scala
val number: Parser[Int] = ???

(number <~ ',').rep
(number <~ ',').rep1
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

# Пример разбора арифметических выражений

```scala
sealed trait Expr
final case class Num(n: Int) extends Expr
final case class Op(o: Char, l: Expr, r: Expr) extends Expr
```

# Пример разбора арифметических выражений

```scala
val digit: Parser[Int] =
  ('0'.p | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9') ^^ { _ - '0' }
```

. . .

```scala
val natural: Parser[Int] =
  digit.rep1 ^^ { _.foldLeft(0)(_ * 10 + _) }
```

# Пример разбора арифметических выражений

```scala
lazy val muldiv: Parser[Expr] =
  brackets ~ ('*'.p | '/') ~ muldiv ^^ {
    case l ~ o ~ r => Op(o, l, r): Expr
  } | brackets
```

. . .

```scala
lazy val addsub: Parser[Expr] =
  muldiv ~ ('+'.p | '-') ~ addsub ^^ {
    case l ~ o ~ r => Op(o, l, r): Expr
  } | muldiv
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
> 7-1-3*2
```

```
Op(-,Num(7),Op(-,Num(1),Op(*,Num(3),Num(2))))
```

. . .

\begin{center}
  \begin{tikzpicture}[
    sibling distance = 7em,
    every node/.style = {
      shape = circle,
      draw,
      align = center,
      minimum size = 2em
    }
  ]
    \node {-}
      child { node {7} }
      child { node {-}
        child { node {1} }
        child { node {*}
          child { node {3} }
          child { node {2} }
        }
      };
  \end{tikzpicture}
\end{center}

# Пример разбора арифметических выражений

\begin{center}
  \begin{tikzpicture}[
    sibling distance = 7em,
    every node/.style = {
      shape = circle,
      draw,
      align = center,
      minimum size = 2em
    }
  ]

    \begin{scope}[xshift=-13em]
      \node {-}
        child { node {a} }
        child { node {-}
          child { node {b} }
          child { node {-}
            child { node {c} }
            child { node {d} }
          }
        };
    \end{scope}

    \begin{scope}[xshift=3.5em]
      \draw[<-, very thick] (-0.9,-2) -- (-1.4,-2);
    \end{scope}

    \begin{scope}[xshift=13em]
      \node {-}
        child { node {-}
          child { node {-}
            child { node {a} }
            child { node {b} }
          }
          child { node {c} }
        }
        child { node {d} };
    \end{scope}

  \end{tikzpicture}
\end{center}

# Пример разбора арифметических выражений

```scala
lazy val muldiv: Parser[Expr] =
  muldiv ~ ('*'.p | '/') ~ brackets ^^ {
    case l ~ o ~ r => Op(o, l, r): Expr
  } | brackets
lazy val addsub: Parser[Expr] =
  addsub ~ ('+'.p | '-') ~ muldiv ^^ {
    case l ~ o ~ r => Op(o, l, r): Expr
  } | muldiv
lazy val brackets: Parser[Expr] =
  '(' ~> addsub <~ ')' | natural ^^ Num
lazy val expr: Parser[Expr] =
  addsub.just
```

# Пример разбора арифметических выражений

```
> 7-1-3*2
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

\begin{center}
  \usetikzlibrary{arrows.meta}
  \begin{tikzpicture}[
    sibling distance = 9em,
    level distance = 6em,
    every node/.style = {
      shape = circle,
      draw,
      align = center
    },
    label/.style = {
      shape = rectangle,
      draw = none,
      above
    },
    sloped
  ]
    \node {}
      child [-{Latex[length=0.7em]}] { node {}
        child [-{Latex[length=0.7em]}] { node {}
          child [-{Latex[length=0.7em]}] {
            node [draw=none] {\Large $\infty$}
            edge from parent node [label] {addsub}
          }
          child [-, dashed] { edge from parent node [label] {muldiv} }
          edge from parent node [label] {addsub}
        }
        child [-, dashed] { edge from parent node [label] {muldiv} }
        edge from parent node [label] {addsub}
      }
      child [-, dashed] { edge from parent node [label] {muldiv} };
  \end{tikzpicture}
\end{center}

# Пример разбора арифметических выражений

```scala
lazy val muldiv: Parser[Expr] =
  brackets ~ (('*'.p | '/') ~ brackets).rep ^^ {
    case e ~ l => l.foldLeft(e) { (acc, nxt) => nxt match {
      case o ~ x => Op(o, acc, x)
    } }
  }
lazy val addsub: Parser[Expr] =
  muldiv ~ (('+'.p | '-') ~ muldiv).rep ^^ {
    case e ~ l => l.foldLeft(e) { (acc, nxt) => nxt match {
      case o ~ x => Op(o, acc, x)
    } }
  }
lazy val brackets: Parser[Expr] =
  '(' ~> addsub <~ ')' | natural ^^ Num
lazy val expr: Parser[Expr] =
  addsub.just
```

::: notes

Если совместить правую рекурсию и повторение, получится неоднозначная грамматика с несколькими вариантами разбора.

:::

# Пример разбора арифметических выражений

```
> 7-1-3*2
```

```
Op(-,Op(-,Num(7),Num(1)),Op(*,Num(3),Num(2)))
```

\begin{center}
  \begin{tikzpicture}[
    every node/.style = {
      shape = circle,
      draw,
      align = center,
      minimum size = 2em
    },
    level 1/.style = {
      sibling distance = 11em
    },
    level 2/.style = {
      sibling distance = 7em
    }
  ]
    \node {-}
      child { node {-}
        child { node {7} }
        child { node {1} }
      }
      child { node {*}
        child { node {3} }
        child { node {2} }
      };
  \end{tikzpicture}
\end{center}

# Реальный мир

```scala
trait Parser[T] extends (String => Either[String, (String, T)])
```

. . .

- Неоднозначные грамматики - плохо
- Разбор одного варианта быстрее
- Интересны ошибки разбора

. . .

Ограничение:

```scala
a ~ ("+" | "++") ~ b
```

# Итог

Плюсы:

- Не нужно изучать язык задания грамматик
- Не нужен механизм подключения парсера к проекту
- Парсер сразу создает нужное нам дерево
- Возможно комбинировать различные парсеры
- Возможно парсить контекстно-зависимые грамматики

. . .

Минусы:

- Левая рекурсия
- Устранение неоднозначности

# Scala библиотеки

- scala-parser-combinators
- Atto
- Parboiled2
- GLL Combinators
- FastParse
- ~~Meerkat~~
- ~~Parsley~~

# scala-parser-combinators

```scala
lazy val muldiv: Parser[Expr] =
  brackets ~ (elem('*') | '/') ~ muldiv ^^ {
    case l ~ o ~ r => Op(o, l, r)
  } | brackets
lazy val addsub: Parser[Expr] =
  muldiv ~ (elem('+') | '-') ~ addsub ^^ {
    case l ~ o ~ r => Op(o, l, r)
  } | muldiv
lazy val brackets: Parser[Expr] =
  '(' ~> addsub <~ ')' | natural ^^ Num
lazy val expr: Parser[Expr] =
  addsub
```

::: notes

Были частью стандартной библиотеки scala.

:::

# Atto

```scala
lazy val muldiv: Parser[Expr] =
  brackets ~ (char('*') | char('/')) ~ muldiv -| {
    case ((l, o), r) => Op(o, l, r)
  } | brackets
lazy val addsub: Parser[Expr] =
  muldiv ~ (char('+') | char('-')) ~ addsub -| {
    case ((l, o), r) => Op(o, l, r)
  } | muldiv
lazy val brackets: Parser[Expr] =
  char('(') ~> addsub <~ char(')') | natural -| Num
lazy val expr: Parser[Expr] =
  addsub
```

# Parboiled2

```scala
def muldiv: Rule1[Expr] = rule {
   brackets ~ push(cursorChar) ~ (ch('*') | '/') ~ muldiv ~> {
     (l, o, r) => Op(o, l, r)
   } | brackets
}
def addsub: Rule1[Expr] = rule {
  muldiv ~ push(cursorChar) ~ (ch('+') | '-') ~ addsub ~> {
    (l, o, r) => Op(o, l, r)
  } | muldiv
}
def brackets: Rule1[Expr] = rule {
  '(' ~ addsub ~ ')' | natural ~> Num
}
def expr: Rule1[Expr] = rule {
  addsub ~ EOI
}
```

# GLL Combinators

```scala
lazy val muldiv: Parser[Expr] =
  brackets ~ ("*" | "/") ~ muldiv ^^ {
    case (l, o, r) => Op(o.head, l, r)
  } | brackets
lazy val addsub: Parser[Expr] =
  muldiv ~ ("+" | "-") ~ addsub ^^ {
    case (l, o, r) => Op(o.head, l, r)
  } | muldiv
lazy val brackets: Parser[Expr] =
  "(" ~> addsub <~ ")" | natural ^^ Num
lazy val expr: Parser[Expr] =
  addsub
```

# FastParse

```scala
def muldiv[_: P]: P[Expr] =
  (brackets ~ (P("*").map(_ => '*') | P("/").map(_ => '/')) ~ muldiv).map {
    case (l, o, r) => Op(o, l, r)
  } | brackets
def addsub[_: P]: P[Expr] =
  (muldiv ~ (P("+").map(_ => '+') | P("-").map(_ => '-')) ~ addsub).map {
    case (l, o, r) => Op(o, l, r)
  } | muldiv
def brackets[_: P]: P[Expr] =
  P(("(" ~/ addsub ~ ")") | natural.map(Num))
def expr[_: P]: P[Expr] =
  addsub ~ End
```
