import scala.language.implicitConversions

trait Parsers {
  case class ~[+A, +B](_1: A, _2: B)

  trait Parser[A, B] extends (Seq[A] => LazyList[(Seq[A], B)]) { self =>
    def p: Parser[A, B] = self

    def map[C](f: B => C): Parser[A, C] = new Parser[A, C] {
      override def apply(seq: Seq[A]): LazyList[(Seq[A], C)] =
        self(seq).map {
          case (tail, b) => tail -> f(b)
        }
    }

    def ^^[C](f: B => C): Parser[A, C] =
      map(f)

    def ~[C](next: => Parser[A, C]): Parser[A, B ~ C] = new Parser[A, B ~ C] {
      override def apply(seq: Seq[A]): LazyList[(Seq[A], B ~ C)] =
        for {
          (tail1, b) <- self(seq)
          (tail2, c) <- next(tail1)
        } yield tail2 -> new ~(b, c)
    }

    def ~>[C](next: => Parser[A, C]): Parser[A, C] =
      self ~ next ^^ { case _ ~ c => c }

    def <~[C](next: => Parser[A, C]): Parser[A, B] =
      self ~ next ^^ { case b ~ _ => b }

    def |(other: => Parser[A, B]): Parser[A, B] = new Parser[A, B] {
      override def apply(seq: Seq[A]): LazyList[(Seq[A], B)] =
        self(seq) ++ other(seq)
    }

    def ? : Parser[A, Option[B]] =
      self ^^ Option.apply | succeed(None)

    def just: Parser[A, B] = new Parser[A, B] {
      override def apply(seq: Seq[A]): LazyList[(Seq[A], B)] =
        self(seq).filter { case (tail, _) => tail.isEmpty }
    }

    def flatMap[C](f: B => Parser[A, C]): Parser[A, C] = new Parser[A, C] {
      override def apply(seq: Seq[A]): LazyList[(Seq[A], C)] =
        for {
          (tail1, b) <- self(seq)
          (tail2, c) <- f(b)(tail1)
        } yield tail2 -> c
    }

    def rep: Parser[A, List[B]] =
      self ~ self.rep ^^ { case h ~ t => h :: t } | succeed(Nil)

    def rep1: Parser[A, List[B]] =
      self ~ self.rep ^^ { case h ~ t => h :: t }

    def run(seq: Seq[A]): Option[B] =
      self(seq).headOption.map { case (_, b) => b }
  }

  def succeed[A, B](v: B): Parser[A, B] = new Parser[A, B] {
    override def apply(seq: Seq[A]): LazyList[(Seq[A], B)] =
      LazyList(seq -> v)
  }

  def fail[A]: Parser[A, Nothing] = new Parser[A, Nothing] {
    override def apply(seq: Seq[A]): LazyList[(Seq[A], Nothing)] =
      LazyList.empty
  }

  def epsilon[A]: Parser[A, Unit] = succeed(())

  def satisfy[A](f: A => Boolean): Parser[A, A] = new Parser[A, A] {
    override def apply(seq: Seq[A]): LazyList[(Seq[A], A)] =
      if (seq.nonEmpty && f(seq.head))
        LazyList(seq.tail -> seq.head)
      else
        LazyList.empty
  }

  implicit def symbol[A](c: A): Parser[A, A] = satisfy(_ == c)

  implicit def token[A](k: Seq[A]): Parser[A, Seq[A]] = new Parser[A, Seq[A]] {
    override def apply(seq: Seq[A]): LazyList[(Seq[A], Seq[A])] =
      if (seq.size >= k.size && k == seq.take(k.size))
        LazyList(seq.drop(k.size) -> k)
      else
        LazyList.empty
  }
}

sealed trait Expr
final case class Num(n: Int) extends Expr
final case class Op(o: Char, l: Expr, r: Expr) extends Expr

trait Natural extends Parsers {
  lazy val digit: Parser[Char, Int] =
    ('0'.p | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9') ^^ { _ - '0' }
  lazy val natural: Parser[Char, Int] =
    digit.rep1 ^^ { _.foldLeft(0)(_ * 10 + _) }
}

trait Arithmetic0 extends Natural {
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
}

trait Arithmetic1 extends Natural {
  lazy val multdiv: Parser[Char, Expr] =
    brackets ~ ('*'.p | '/') ~ multdiv ^^ {
      case l ~ o ~ r => Op(o, l, r): Expr
    } | brackets
  lazy val addsub: Parser[Char, Expr] =
    multdiv ~ ('+'.p | '-') ~ addsub ^^ {
      case l ~ o ~ r => Op(o, l, r): Expr
    } | multdiv
  lazy val brackets: Parser[Char, Expr] =
    '(' ~> addsub <~ ')' | natural ^^ Num
  lazy val expr: Parser[Char, Expr] =
    addsub.just
}

trait Arithmetic2 extends Natural {
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
}

object Main extends App with Arithmetic2 {
  val input = scala.io.StdIn.readLine()
  val result = expr.run(input)
  println(result)
}
