@file:Suppress(
    "ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_MEMBERS_AS_NON_FINAL_EXPECT_CLASSIFIER_WARNING",
    "NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS", "ACTUAL_WITHOUT_EXPECT", "EXPECT_ACTUAL_INCOMPATIBILITY",
    "EXPECT_ACTUAL_INCOMPATIBLE_MODALITY",
    "EXPECT_ACTUAL_INCOMPATIBLE_CLASS_SCOPE",
)

package com.wynime.utils.xml

actual typealias Document = org.jsoup.nodes.Document
actual typealias Node = org.jsoup.nodes.Node
actual typealias Element = org.jsoup.nodes.Element
actual typealias Elements = org.jsoup.select.Elements
actual typealias Evaluator = org.jsoup.select.Evaluator
