package scalaz

import CostateT._
import Leibniz._
import Id._

/**
 * Partial Lens, offering a purely functional means to access and retrieve
 * an optional field of type `B` in a record of type `A`.
 *
 * This structure is more general than the one described in [[http://days2012.scala-lang.org/sites/days2012/files/morris_lenses.pdf]]
 * as it abstracts over a type constructor `F`, used to address the field, and `G`, used to wrap the value of the field.
 *
 * If `F` and `G` as taken to be [[scalaz.Id]], the structure simplifies to the partial lens presented in the paper.
 *
 * The term ''field'' should not be interpreted restrictively to mean a member of a class. For example, a partial lens
 * can address the nth element of a `List`.
 *
 * @see [[scalaz.LensT]]
 *
 * @tparam F Type constructor used to address the field
 * @tparam G Type constructor used to wrap the value
 * @tparam A The type of the record
 * @tparam B The type of the optional field
 */
sealed trait PLensT[F[+_], G[+_], A, B] {
  def run(a: A): F[Option[Costate[B, G[A]]]]

  def apply(a: A): F[Option[Costate[B, G[A]]]] =
    run(a)

  def runO(a: A): OptionT[F, Costate[B, G[A]]] =
    OptionT(run(a))

  import StateT._
  import PLensT._
  import BijectionT._

  def kleisli: Kleisli[({type λ[+α] = OptionT[F, α]})#λ, A, Costate[B, G[A]]] =
    Kleisli[({type λ[+α] = OptionT[F, α]})#λ, A, Costate[B, G[A]]](runO(_))

  def mapC[C](f: Costate[B, G[A]] => Costate[C, G[A]])(implicit FF: Functor[F]): PLensT[F, G, A, C] =
    plensT(a => FF.map(run(a))(_ map f))

  def xmapA[X](f: A => X, g: X => A)(implicit FF: Functor[F], GF: Functor[G]): PLensT[F, G, X, B] =
    plensO(x => runO(g(x)) map (_ map (GF.map(_)(f))))

  def xmapbA[X](b: Bijection[A, X])(implicit FF: Functor[F], GF: Functor[G]): PLensT[F, G, X, B] =
    xmapA(b to _, b from _)

  def xmapB[X](f: B => X, g: X => B)(implicit FF: Functor[F], GF: Functor[G]): PLensT[F, G, A, X] =
    plensO(a => runO(a) map (_ xmap (f, g)))

  def xmapbB[X](b: Bijection[B, X])(implicit FF: Functor[F], GF: Functor[G]): PLensT[F, G, A, X] =
    xmapB(b to _, b from _)

  def lift[X[+_]](implicit P: Pointed[X], FF: Functor[F], GF: Functor[G]): PLensT[({type λ[+α] = F[X[α]]})#λ, ({type λ[+α] = G[X[α]]})#λ, A, B] =
    plensT[({type λ[+α] = F[X[α]]})#λ, ({type λ[+α] = G[X[α]]})#λ, A, B](a => FF.map(run(a))(c => P.point(c map (_ map (GF.map(_)(P.point(_)))))))

  def get(a: A)(implicit F: Functor[F]): F[Option[B]] =
    F.map(run(a))(_ map (_.pos))

  def getO(a: A)(implicit F: Functor[F]): OptionT[F, B] =
    OptionT(get(a))

  def getK(implicit F: Functor[F]): Kleisli[({type λ[+α] = OptionT[F, α]})#λ, A, B] =
    Kleisli[({type λ[+α] = OptionT[F, α]})#λ, A, B](getO(_))

  /** If the PartialLens is null, then return the given default value. */
  def getOr(a: A, b: => B)(implicit F: Functor[F]): F[B] =
    F.map(get(a))(_ getOrElse b)

  def getOrZ(a: A)(implicit M: Monoid[B], F: Functor[F]): F[B] =
    getOr(a, M.zero)

  def set(a: A, b: B)(implicit F: Functor[F]): F[Option[G[A]]] =
    F.map(run(a))(_ map (_.put(b)))

  def setO(a: A, b: B)(implicit F: Functor[F]): OptionT[F, G[A]] =
    OptionT(set(a, b))

  def setK(a: A)(implicit F: Functor[F]): Kleisli[({type λ[+α] = OptionT[F, α]})#λ, B, G[A]] =
    Kleisli[({type λ[+α] = OptionT[F, α]})#λ, B, G[A]](setO(a, _))

  def setOr(a: A, b: B, d: => G[A])(implicit F: Functor[F]): F[G[A]] =
    F.map(set(a, b))(_ getOrElse d)

  def setOrZ(a: A, b: B)(implicit M: Monoid[G[A]], F: Functor[F]): F[G[A]] =
    setOr(a, b, M.zero)

  def trySet(a: A)(implicit F: Functor[F]): F[Option[Kleisli[G, B, A]]] =
    F.map(run(a))(_ map (c => Kleisli[G, B, A](c put _)))

  def trySetO(a: A)(implicit F: Functor[F]): OptionT[F, Kleisli[G, B, A]] =
    OptionT(trySet(a))

  def trySetK(implicit F: Functor[F]): Kleisli[({type λ[+α] = OptionT[F, α]})#λ, A, Kleisli[G, B, A]] =
    Kleisli[({type λ[+α] = OptionT[F, α]})#λ, A, Kleisli[G, B, A]](trySetO(_))

  def trySetOr(a: A, d: => Kleisli[G, B, A])(implicit F: Functor[F]): F[Kleisli[G, B, A]] =
    F.map(trySet(a))(_ getOrElse d)

  def trySetOrZ(a: A)(implicit M: Monoid[Kleisli[G, B, A]], F: Functor[F]): F[Kleisli[G, B, A]] =
    trySetOr(a, M.zero)

  /** If the PartialLens is null, then return the target object, otherwise run the function on its projection. */
  def as(f: B => A, a: A)(implicit F: Functor[F]): F[A] =
    F.map(get(a))(_ match {
      case None => a
      case Some(w) => f(w)
    })

  def is(a: A)(implicit F: Functor[F]): F[Boolean] =
    F.map(run(a))(_.isDefined)

  def isNot(a: A)(implicit F: Functor[F]): F[Boolean] =
    F.map(is(a))(!_)

  def exists(p: B => Boolean, a: A)(implicit F: Functor[F]): F[Boolean] =
    F.map(get(a))(_ exists p)

  def forall(p: B => Boolean, a: A)(implicit F: Functor[F]): F[Boolean] =
    F.map(get(a))(_ forall p)

  def option(implicit M: Pointed[F], GF: Functor[G]): PLensT[F, G, Option[A], B] =
    plensT {
      case None => M.point(None)
      case Some(w) => M.map(run(w))(_ map (_ map (GF.map(_)(Some(_)))))
    }

  /** An alias for `option`. */
  def unary_!(implicit M: Pointed[F], GF: Functor[G]): PLensT[F, G, Option[A], B] =
    option

  /** Modify the value viewed through the lens */
  def mod(f: B => B, a: A)(implicit F: Functor[F], ev: G[A] === Id[A]): F[A] =
    F.map(run(a)){
      case None => a
      case Some(w) => ev.subst[Id](w puts f)
    }

  def =>=(f: B => B)(implicit F: Functor[F], ev: G[A] === Id[A]): A => F[A] =
    mod(f, _)

  def st(implicit F: Functor[F]): PStateT[F, A, B] =
    StateT(s => F.map(get(s))((s, _)))

  def %=(f: B => B)(implicit F: Functor[F], ev: G[A] === Id[A]): PStateT[F, A, B] =
    StateT(a => F.map(run(a))(_ match {
      case None => (a, None)
      case Some(w) => {
        val r = f(w.pos)
        (ev.subst[Id](w put r), Some(r))
      }
    }))

  def :=(b: => B)(implicit F: Functor[F], ev: G[A] === Id[A]): PStateT[F, A, B] =
    %=(_ => b)

  def %==(f: B => B)(implicit F: Functor[F], ev: G[A] === Id[A]): StateT[F, A, Unit] =
    StateT(a =>
      F.map(mod(f, a))((_, ())))

  def %%=[C](s: State[B, C])(implicit F: Functor[F], ev: G[A] === Id[A]): PStateT[F, A, C] =
    StateT(a => F.map(run(a))(_ match {
      case None => (a, None)
      case Some(w) => {
        val r = s.run(w.pos): (B, C)
        (ev.subst[Id](w put r._1), Some(r._2))
      }
    }))

  def >-[C](f: B => C)(implicit F: Functor[F], ev: G[A] === Id[A]): PStateT[F, A, C] =
    StateT(a => F.map(get(a))(x => (a, x map f)))

  def >>-[C](f: B => StateT[F, A, C])(implicit F: Monad[F], ev: G[A] === Id[A]): PStateT[F, A, C] =
    StateT(a => F.bind(get(a))(_ match {
      case None => F.point((a, None))
      case Some(w) =>
        F.map(f(w) apply a) {
          case (y, x) => (y, Some(x))
        }
    }))

  def ->>-[C](f: => StateT[F, A, C])(implicit F: Monad[F], ev: G[A] === Id[A]): PStateT[F, A, C] =
    >>-(_ => f)

  /** Lenses can be composed */
  def compose[C](that: PLensT[F, G, C, A])(implicit FF: Monad[F], GF: Monad[G]): PLensT[F, G, C, B] =
    plensO(c =>
      (that runO c).flatMap (x => {
        val (ac, a) = x.run
        runO(a) map (y => {
          val (ba, b) = y.run
          Costate(x => GF.bind(ba(x))(ac), b)
        })
      }))

  /** alias for `compose` */
  def <=<[C](that: PLensT[F, G, C, A])(implicit FF: Monad[F], GF: Monad[G]): PLensT[F, G, C, B] = compose(that)

  def andThen[C](that: PLensT[F, G, B, C])(implicit FF: Monad[F], GF: Monad[G]): PLensT[F, G, A, C] =
    that compose this

  /** alias for `andThen` */
  def >=>[C](that: PLensT[F, G, B, C])(implicit FF: Monad[F], GF: Monad[G]): PLensT[F, G, A, C] = andThen(that)

  /** Two lenses that view a value of the same type can be joined */
  def sum[C](that: => PLensT[F, G, C, B])(implicit FF: Functor[F], GF: Functor[G]): PLensT[F, G, Either[A, C], B] =
    plensT{
      case Left(a) =>
        FF.map(run(a))(_ map (_ map (GF.map(_)(Left(_)))))
      case Right(c) =>
        FF.map(that run c)(_ map (_ map (GF.map(_)(Right(_)))))
    }

  /** Alias for `sum` */
  def |||[C](that: => PLensT[F, G, C, B])(implicit FF: Functor[F], GF: Functor[G]): PLensT[F, G, Either[A, C], B] = sum(that)

  /** Two disjoint lenses can be paired */
  def product[C, D](that: PLensT[F, G, C, D])(implicit FF: Apply[F], GG: Apply[G]): PLensT[F, G, (A, C), (B, D)] =
    plensT {
      case (a, c) =>
        FF.map2(run(a), that run c)((x, y) => for {
          q <- x
          r <- y
        } yield q *** r map {
          case (s, t) => GG.map2(s, t)((i, j) => (i, j))
        })
    }

  /** alias for `product` */
  def ***[C, D](that: PLensT[F, G, C, D])(implicit FF: Apply[F], GG: Apply[G]): PLensT[F, G, (A, C), (B, D)] = product(that)

}

object PLensT extends PLensTFunctions with PLensTInstances {
  def apply[F[+_], G[+_], A, B](r: A => F[Option[Costate[B, G[A]]]]): PLensT[F, G, A, B] =
    plensT(r)
}

trait PLensTFunctions extends PLensTInstances {

  import CostateT._
  import BijectionT._

  def plensT[F[+_], G[+_], A, B](r: A => F[Option[Costate[B, G[A]]]]): PLensT[F, G, A, B] = new PLensT[F, G, A, B] {
    def run(a: A): F[Option[Costate[B, G[A]]]] = r(a)
  }

  def plensO[F[+_], G[+_], A, B](r: A => OptionT[F, Costate[B, G[A]]]): PLensT[F, G, A, B] =
    plensT(a => r(a).run)

  def plens[A, B](r: A => Option[Costate[B, A]]): PLens[A, B] =
    plensT[Id, Id, A, B](r)

  def plensp[F[+_], G[+_], A, B](r: A => Option[Costate[B, A]])(implicit PF: Pointed[F], PG: Pointed[G]): PLensT[F, G, A, B] =
    plensT(a => PF.point(r(a) map (_ map  (PG.point(_)))))

  def plensgT[F[+_], G[+_], A, B](set: A => F[Option[B => G[A]]], get: A => F[Option[B]])(implicit M: Bind[F]): PLensT[F, G, A, B] =
    plensT(a => M.map2(set(a), get(a))((q, r) => for {
      w <- q
      x <- r
    } yield Costate(w, x)))

  def plensgO[F[+_], G[+_], A, B](set: A => OptionT[F, B => G[A]], get: A => OptionT[F, B])(implicit M: Bind[F]): PLensT[F, G, A, B] =
    plensgT(a => set(a).run, a => get(a).run)

  def plensg[A, B](set: A => Option[B => A], get: A => Option[B]): PLens[A, B] =
    plensgT[Id, Id, A, B](set, get)

  /** The identity partial lens for a given object */
  def plensId[F[+_], G[+_], A](implicit FF: Pointed[F], GF: Pointed[G]): PLensT[F, G, A, A] =
    LensT.lensId[F, G, A].partial

  /** The trivial partial lens that can retrieve Unit from anything */
  def trivialPLens[F[+_], G[+_], A](implicit FF: Pointed[F], GF: Pointed[G]): PLensT[F, G, A, Unit] =
    LensT.trivialLens[F, G, A].partial

  /** A lens that discards the choice of Right or Left from Either */
  def codiagPLens[F[+_]: Pointed, G[+_]: Pointed, A]: PLensT[F, G, Either[A, A], A] =
    plensId[F, G, A] ||| plensId[F, G, A]

  /** The always-null partial lens */
  def nil[F[+_]: Pointed, G[+_]: Pointed, A, B]: PLensT[F, G, A, B] =
    plensp(_ => None)

  def somePLens[A]: Option[A] @?> A =
    plens(_ map (z => Costate(Some(_), z)))

  def leftPLens[A, B]: Either[A, B] @?> A =
    plens {
      case Left(a) => Some(Costate(Left(_), a))
      case Right(_) => None
    }

  def rightPLens[A, B]: Either[A, B] @?> B =
    plens {
      case Right(b) => Some(Costate(Right(_), b))
      case Left(_) => None
    }

  def tuple2PLens[F[+_]: Functor, G[+_]: Functor, S, A, B](lens: PLensT[F, G, S, (A, B)]):
  (PLensT[F, G, S, A], PLensT[F, G, S, B]) =
    PLensTUnzip[F, G, S].unzip(lens)

  def tuple3PLens[F[+_]: Functor, G[+_]: Functor, S, A, B, C](lens: PLensT[F, G, S, (A, B, C)]):
  (PLensT[F, G, S, A], PLensT[F, G, S, B], PLensT[F, G, S, C]) =
    PLensTUnzip[F, G, S].unzip3(lens.xmapbB(tuple3B))

  def tuple4PLens[F[+_]: Functor, G[+_]: Functor, S, A, B, C, D](lens: PLensT[F, G, S, (A, B, C, D)]):
  (PLensT[F, G, S, A], PLensT[F, G, S, B], PLensT[F, G, S, C], PLensT[F, G, S, D]) =
    PLensTUnzip[F, G, S].unzip4(lens.xmapbB(tuple4B))

  def tuple5PLens[F[+_]: Functor, G[+_]: Functor, S, A, B, C, D, E](lens: PLensT[F, G, S, (A, B, C, D, E)]):
  (PLensT[F, G, S, A], PLensT[F, G, S, B], PLensT[F, G, S, C], PLensT[F, G, S, D], PLensT[F, G, S, E]) =
    PLensTUnzip[F, G, S].unzip5(lens.xmapbB(tuple5B))

  def tuple6PLens[F[+_]: Functor, G[+_]: Functor, S, A, B, C, D, E, H](lens: PLensT[F, G, S, (A, B, C, D, E, H)]):
  (PLensT[F, G, S, A], PLensT[F, G, S, B], PLensT[F, G, S, C], PLensT[F, G, S, D], PLensT[F, G, S, E], PLensT[F, G, S, H]) =
    PLensTUnzip[F, G, S].unzip6(lens.xmapbB(tuple6B))

  def tuple7PLens[F[+_]: Functor, G[+_]: Functor, S, A, B, C, D, E, H, I](lens: PLensT[F, G, S, (A, B, C, D, E, H, I)]):
  (PLensT[F, G, S, A], PLensT[F, G, S, B], PLensT[F, G, S, C], PLensT[F, G, S, D], PLensT[F, G, S, E], PLensT[F, G, S, H], PLensT[F, G, S, I]) =
    PLensTUnzip[F, G, S].unzip7(lens.xmapbB(tuple7B))

  def eitherLens[S, A, B](l: S @?> Either[A, B]): (S @?> A, S @?> B) =
    (
    leftPLens compose l
    , rightPLens compose l
    )

  import LazyOption._

  def lazySomePLens[A]: LazyOption[A] @?> A =
    plens(_.fold(z => Some(Costate(lazySome(_), z)), None))

  import LazyEither._

  def lazyLeftPLens[A, B]: LazyEither[A, B] @?> A =
    plens(_.fold(a => Some(Costate(lazyLeft(_), a)), _ => None))

  def lazyRightPLens[A, B]: LazyEither[A, B] @?> B =
    plens(_.fold(_ => None, b => Some(Costate(lazyRight(_), b))))

  def listHeadPLens[A]: List[A] @?> A =
    plens {
      case Nil => None
      case h :: t => Some(Costate(_ :: t, h))
    }

  def listTailPLens[A]: List[A] @?> List[A] =
    plens {
      case Nil => None
      case h :: t => Some(Costate(h :: _, t))
    }

  def listNthPLens[A](n: Int): List[A] @?> A =
    if(n < 0)
      nil
    else if(n == 0)
      listHeadPLens
    else
      listNthPLens(n - 1) compose listTailPLens

  def listLookupByPLens[K, V](p: K => Boolean): List[(K, V)] @?> V = {
    @annotation.tailrec
    def lookupr(t: (List[(K, V)], (K, V), List[(K, V)])): Option[(List[(K, V)], (K, V), List[(K, V)])] =
      t match {
        case (_, (k, _), _) if p(k) => Some(t)
        case (_, _     , Nil)       => None
        case (l, x     , r::rs)     => lookupr(x::l, r, rs)
      }
    plens {
      case Nil => None
      case h :: t => lookupr(Nil, h, t) map {
        case (l, (k, v), r) => Costate(w => l.reverse ::: (k, w) :: r, v)
      }
    }
  }

  def listLookupPLens[K: Equal, V](k: K): List[(K, V)] @?> V =
    listLookupByPLens(Equal[K].equal(k, _))

  def vectorHeadPLens[A]: Vector[A] @?> A =
    vectorNthPLens(0)

  def vectorNthPLens[A](n: Int): Vector[A] @?> A =
    plens(v =>
      v.lift(n) map (a => Costate(x => v patch (n, Vector(x), 1), a)))

  def vectorLastPLens[A]: Vector[A] @?> A =
    plens(v =>
      v.lastOption map (a => Costate(x => v patch (v.length - 1, Vector(x), 1), a)))

  import Stream._

  def streamHeadPLens[A]: Stream[A] @?> A =
    plens {
      case Empty => None
      case h #:: t => Some(Costate(_ #:: t, h))
    }

  def streamTailPLens[A]: Stream[A] @?> Stream[A] =
    plens {
      case Empty => None
      case h #:: t => Some(Costate(h #:: _, t))
    }

  def streamNthPLens[A](n: Int): Stream[A] @?> A =
    if(n < 0)
      nil
    else if(n == 0)
      streamHeadPLens
    else
      streamNthPLens(n - 1) compose streamTailPLens

  def streamLookupByPLens[K, V](p: K => Boolean): Stream[(K, V)] @?> V = {
    @annotation.tailrec
    def lookupr(t: (Stream[(K, V)], (K, V), Stream[(K, V)])): Option[(Stream[(K, V)], (K, V), Stream[(K, V)])] =
      t match {
        case (_, (k, _), _) if p(k)    => Some(t)
        case (_, _     , Stream.Empty) => None
        case (l, x     , r #:: rs)     => lookupr(x #:: l, r, rs)
      }
    plens {
      case Stream.Empty => None
      case h #:: t => lookupr(Stream.empty, h, t) map {
        case (l, (k, v), r) => Costate(w => l.reverse #::: (k, w) #:: r, v)
      }
    }
  }

  def streamLookupPLens[K: Equal, V](k: K): Stream[(K, V)] @?> V =
    streamLookupByPLens(Equal[K].equal(k, _))

  def ephemeralStreamHeadPLens[A]: EphemeralStream[A] @?> A =
    plens(s =>
      if(s.isEmpty)
        None
      else
        Some(Costate(EphemeralStream.cons(_, s.tail()), s.head()))
    )

  def ephemeralStreamTailPLens[A]: EphemeralStream[A] @?> EphemeralStream[A] =
    plens(s =>
      if(s.isEmpty)
        None
      else
        Some(Costate(EphemeralStream.cons(s.head(), _), s.tail()))
    )

  def ephemeralStreamNthPLens[A](n: Int): EphemeralStream[A] @?> A =
    if(n < 0)
      nil
    else if(n == 0)
      ephemeralStreamHeadPLens
    else
      ephemeralStreamNthPLens(n - 1) compose ephemeralStreamTailPLens

  def ephemeralStreamLookupByPLens[K, V](p: K => Boolean): EphemeralStream[(K, V)] @?> V = {
    import EphemeralStream.cons

    @annotation.tailrec
    def lookupr(t: (EphemeralStream[(K, V)], (K, V), EphemeralStream[(K, V)])): Option[(EphemeralStream[(K, V)], (K, V), EphemeralStream[(K, V)])] =
      t match {
        case (_, (k, _), _) if p(k)    => Some(t)
        case (l, x     , s) =>
            if(s.isEmpty)
              None
            else
              lookupr((cons(x, l), s.head(), s.tail()))
      }
    plens(s =>
      if(s.isEmpty)
        None
      else
        lookupr((EphemeralStream.emptyEphemeralStream, s.head(), s.tail())) map {
          case (l, (k, v), r) => Costate(w => l.reverse ++ cons((k, w), r), v)
        }
    )
  }

  def ephemeralStreamLookupPLens[K: Equal, V](k: K): EphemeralStream[(K, V)] @?> V =
    ephemeralStreamLookupByPLens(Equal[K].equal(k, _))

  import LensT.mapVLens

  def mapVPLens[K, V](k: K): Map[K, V] @?> V =
    somePLens compose ~mapVLens[K, V](k)

  def factorPLens[A, B, C]: Either[(A, B), (A, C)] @?> (A, Either[B, C]) =
    ~LensT.factorLens

  def distributePLens[A, B, C]: (A, Either[B, C]) @?> Either[(A, B), (A, C)] =
    ~LensT.distributeLens

  import util.parsing.json._

  def scalaJSONObjectPLens[A]: JSONType @?> Map[String, Any] =
    plens {
      case JSONObject(m) => Some(Costate(JSONObject(_), m))
      case _             => None
    }

  def scalaJSONArrayPLens[A]: JSONType @?> List[Any] =
    plens {
      case JSONArray(a) => Some(Costate(JSONArray(_), a))
      case _            => None
    }
}

trait PLensTInstance0 {
  implicit def plensTArrId[F[+_], G[+_]](implicit F0: Pointed[F], G0: Pointed[G]) = new PLensTArrId[F, G] {
    implicit def F = F0
    implicit def G = G0
  }
}

trait PLensTInstances extends PLensTInstance0 {
  import PLensT._

  implicit def plensTCategory[F[+_], G[+_]](implicit F0: Monad[F], G0: Monad[G]) = new PLensTCategory[F, G] {
    implicit def F = F0
    implicit def G = G0
  }

  /** Lenses may be used implicitly as State monadic actions that get the viewed portion of the state */
  implicit def PLensState[F[+_], G[+_], A, B](plens: PLensT[F, G, A, B])(implicit F: Functor[F]): PStateT[F, A, B] =
    plens.st

  implicit def PLensTUnzip[F[+_], G[+_], S](implicit FF: Functor[F], GF: Functor[G]): Unzip[({type λ[α] = PLensT[F, G, S, α]})#λ] =
    new Unzip[({type λ[α] = PLensT[F, G, S, α]})#λ] {
      def unzip[A, B](a: PLensT[F, G, S, (A, B)]) =
        (
          PLensT(x => FF.map(a run x)(_ map (c => {
            val (p, q) = c.pos
            Costate(a => c.put((a, q)): G[S], p)
          })))
          , PLensT(x => FF.map(a run x)(_ map (c => {
          val (p, q) = c.pos
          Costate(a => c.put((p, a)): G[S], q)
        })))
          )
    }

  /** Allow the illusion of imperative updates to numbers viewed through a partial lens */
  case class NumericPLens[S, N: Numeric](lens: S @?> N, num: Numeric[N]) {
    def +=(that: N): PState[S, N] =
      lens %= (num.minus(_, that))

    def -=(that: N): PState[S, N] =
      lens %= (num.minus(_, that))

    def *=(that: N): PState[S, N] =
      lens %= (num.times(_, that))
  }

  implicit def numericPLens[S, N: Numeric](lens: S @?> N) =
    NumericPLens[S, N](lens, implicitly[Numeric[N]])

  /** Allow the illusion of imperative updates to numbers viewed through a partial lens */
  case class FractionalPLens[S, F](lens: S @?> F, frac: Fractional[F]) {
    def /=(that: F): PState[S, F] =
      lens %= (frac.div(_, that))
  }

  implicit def fractionalPLens[S, F: Fractional](lens: S @?> F) =
    FractionalPLens[S, F](lens, implicitly[Fractional[F]])

  /** Allow the illusion of imperative updates to numbers viewed through a partial lens */
  case class IntegralPLens[S, I](lens: S @?> I, ig: Integral[I]) {
    def %=(that: I): PState[S, I] =
      lens %= (ig.quot(_, that))
  }

  implicit def integralPLens[S, I: Integral](lens: S @?> I) =
    IntegralPLens[S, I](lens, implicitly[Integral[I]])
}

private[scalaz] trait PLensTArrId[F[+_], G[+_]] extends ArrId[({type λ[α, β] = PLensT[F, G, α, β]})#λ] {
  implicit def F: Pointed[F]
  implicit def G: Pointed[G]

  def id[A]: PLensT[F, G, A, A] = PLensT.plensId
}

private[scalaz] trait PLensTCategory[F[+_], G[+_]]
  extends Choice[({type λ[α, β] = PLensT[F, G, α, β]})#λ]
  with Split[({type λ[α, β] = PLensT[F, G, α, β]})#λ]
  with PLensTArrId[F, G] {

  implicit def F: Monad[F]
  implicit def G: Monad[G]

  def compose[A, B, C](bc: PLensT[F, G, B, C], ab: PLensT[F, G, A, B]): PLensT[F, G, A, C] = ab >=> bc

  def choice[A, B, C](f: => PLensT[F, G, A, C], g: => PLensT[F, G, B, C]): PLensT[F, G, Either[A, B], C] =
    PLensT.plensT[F, G, Either[A, B], C] {
      case Left(a) =>
        F.map(f run a)(_ map (_ map (G.map(_)(Left(_)))))
      case Right(b) =>
        F.map(g run b)(_ map (_ map (G.map(_)(Right(_)))))
    }

  def split[A, B, C, D](f: PLensT[F, G, A, B], g: PLensT[F, G, C, D]): PLensT[F, G, (A,  C), (B, D)] =
    f *** g
}
