package helios.sample

import arrow.core.Either
import arrow.core.extensions.either.applicative.product
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import helios.core.*
import helios.instances.decoder
import helios.json
import helios.typeclasses.Decoder
import helios.typeclasses.Encoder

sealed class Test {
    companion object
}

@json
data class A(val a: String) : Test() {
    companion object
}

@json
data class B(val b: String) : Test() {
    companion object
}

fun Test.Companion.encoder() = object : Encoder<Test> {
    override fun Test.encode(): Json = let {
        JsObject(mapOf(
            "type" to JsString(it::class.java.simpleName),
            "value" to when (it) {
                is A -> with(A.encoder()) { it.encode() }
                is B -> with(B.encoder()) { it.encode() }
            }))
    }
}

fun typeDecoder(type: String): Either<DecodingError, Decoder<Test>> = when(type) {
    A::class.java.simpleName -> A.decoder().right()
    B::class.java.simpleName -> B.decoder().right()
    else ->  StringDecodingError(JsString("$type type invalid")).left()
}

fun Test.Companion.decoder() = object: Decoder<Test> {
    override fun decode(value: Json): Either<DecodingError, Test> =
        value["type"].fold({ KeyNotFound("type").left() }, { String.decoder().decode(it).flatMap(::typeDecoder) })
            .product(value["value"] .fold({ KeyNotFound("value").left() }, {it.right() }))
            .flatMap { (decoder, value) -> decoder.decode(value) }
}

object Sample {

    @JvmStatic
    fun main(args: Array<String>) {
        println(with(Test.encoder()) { B(b = "Coucou").encode() }.toJsonString())
        // {"type":"B","value":{"b":"Coucou"}}
        println(Json.parseFromString("""{"type":"B","value":{"b":"Valeur pour le type B"}}""").flatMap { Test.decoder().decode(it) })
        // {"type":"B","value":{"b":"Valeur pour le type B"}}
        println(with(Test.encoder()) { A(a = "Valeur pour le type A").encode() }.toJsonString())
        // {"type":"A","value":{"a":"Valeur pour le type A"}}
        println(Json.parseFromString("""{"type":"A","value":{"a":"Valeur pour le type A"}}""").flatMap { Test.decoder().decode(it) })
        // Right(b=A(a=Valeur pour le type A))
        println(Json.parseFromString("""{"value":{"a":"Valeur pour le type A"}}""").flatMap { Test.decoder().decode(it) })
        // Left(a=KeyNotFound(name=type))
        println(Json.parseFromString("""{"type":"C","value":{"a":"Valeur pour le type A"}}""").flatMap { Test.decoder().decode(it) })
        // Left(a=StringDecodingError(value=JsString(value=type invalid)))
        println(Json.parseFromString("""{"type":"A"}""").flatMap { Test.decoder().decode(it) })
        // Left(a=KeyNotFound(name=value))
        println(Json.parseFromString("""{"type":"A", "value":{"b":"Valeur pour le type B"}}""").flatMap { Test.decoder().decode(it) })
        // Left(a=KeyNotFound(name=a))
    }
}