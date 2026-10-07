@file:Suppress(
    "ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_MEMBERS_AS_NON_FINAL_EXPECT_CLASSIFIER_WARNING",
    "NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS", "ACTUAL_WITHOUT_EXPECT", "EXPECT_ACTUAL_INCOMPATIBILITY",
    "EXPECT_ACTUAL_INCOMPATIBLE_MODALITY",
    "EXPECT_ACTUAL_INCOMPATIBLE_CLASS_SCOPE",

    "EXPECT_ACTUAL_IR_INCOMPATIBILITY",
    "EXPECT_ACTUAL_IR_MISMATCH",
)

package com.wynime.utils.xml

@Suppress("EXPECT_ACTUAL_INCOMPATIBILITY")
expect abstract class Document : Element

@Suppress("EXPECT_ACTUAL_INCOMPATIBILITY")
expect abstract class Element : Node {
    fun tagName(): String

    open fun getElementsByTag(tagName: String): Elements

    open fun getElementById(id: String): Element?

    open fun getElementsByClass(className: String): Elements

    open fun getElementsByAttribute(key: String): Elements

    open fun getElementsByAttributeStarting(keyPrefix: String): Elements

    open fun getElementsByAttributeValue(
        key: String,
        value: String,
    ): Elements

    open fun getElementsByAttributeValueNot(
        key: String,
        value: String,
    ): Elements

    open fun getElementsByAttributeValueStarting(
        key: String,
        valuePrefix: String,
    ): Elements

    open fun getElementsByAttributeValueEnding(
        key: String,
        valueSuffix: String,
    ): Elements

    open fun getElementsByAttributeValueContaining(
        key: String,
        match: String,
    ): Elements

    open fun getElementsByAttributeValueMatching(
        key: String,
        regex: String,
    ): Elements

    open fun getElementsByIndexLessThan(index: Int): Elements

    open fun getElementsByIndexGreaterThan(index: Int): Elements

    open fun getElementsByIndexEquals(index: Int): Elements

    open fun getElementsContainingText(searchText: String): Elements

    open fun getElementsContainingOwnText(searchText: String): Elements

    open fun getAllElements(): Elements

    open fun text(): String

    @Deprecated("This will throw if the cssQuery in invalid. Use the overload that uses a evaluator instead.")
    fun select(cssQuery: String): Elements
    fun select(evaluator: Evaluator): Elements

    fun childrenSize(): Int
    fun children(): Elements
}

@Suppress("EXPECT_ACTUAL_INCOMPATIBILITY")
expect abstract class Evaluator

@Suppress("EXPECT_ACTUAL_INCOMPATIBILITY")
expect abstract class Elements : List<Element> {
    fun attr(attributeKey: String): String
    fun text(): String
}

@Suppress("EXPECT_ACTUAL_INCOMPATIBILITY")
expect abstract class Node {

    open fun attr(attributeKey: String): String

    open fun attributesSize(): Int

    @Suppress("EXPECT_ACTUAL_MISMATCH")
    open fun attr(
        attributeKey: String,
        attributeValue: String?,
    ): Node

    open fun hasAttr(attributeKey: String): Boolean

    open fun removeAttr(attributeKey: String): Node

    open fun clearAttributes(): Node

    open fun setBaseUri(baseUri: String)

    open fun absUrl(attributeKey: String): String

    open fun childNode(index: Int): Node

    open fun childNodes(): List<Node>

    open fun childNodesCopy(): List<Node>

    abstract fun childNodeSize(): Int

    abstract fun empty(): Node?

    open fun parent(): Node?

    fun parentNode(): Node?

    open fun root(): Node

    open fun ownerDocument(): Document?

    open fun remove()

    open fun before(html: String): Node

    open fun before(node: Node): Node

    open fun after(html: String): Node

    open fun after(node: Node): Node

    open fun wrap(html: String): Node

    open fun unwrap(): Node?

    open fun siblingNodes(): List<Node>

    open fun nextSibling(): Node?

    open fun previousSibling(): Node?

    open fun siblingIndex(): Int

    open fun firstChild(): Node?

    open fun lastChild(): Node?

    open fun <T : Appendable> html(appendable: T): T

    open fun hasSameValue(o: Any?): Boolean
}

