/*
 * Copyright (c) 2016 大前良介 (OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.android.upnp.cds

import net.mm2d.upnp.util.asIterable
import org.w3c.dom.Element

/**
 * シンプルなXMLのタグ情報を表現するクラス
 *
 * Elementのままでは情報の参照コストが高いため、
 * よりシンプルな構造に格納するためのクラス。
 * CdsObjectのXMLのようにElementが入れ子になることのない
 * タグ＋値、属性＋値の情報を表現できれば十分なものを表現するのに使用する。
 * 入れ子関係を持つXMLは表現できない。
 *
 * @author [大前良介 (OHMAE Ryosuke)](mailto:ryo@mm2d.net)
 */
class Tag(
    val name: String,
    val value: String,
    val attributes: Map<String, String>
) {
    /**
     * 属性値を返す。
     *
     * @param name 属性名
     * @return 属性値、見つからない場合null
     */
    fun getAttribute(name: String?): String? = attributes[name]

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(value)
        attributes.forEach {
            sb.append("\n@${it.key} => ${it.value}")
        }
        return sb.toString()
    }

    companion object {
        val EMPTY = Tag("", "", emptyMap())
        /**
         * インスタンス作成。
         *
         * パッケージ外でのインスタンス化禁止
         *
         * @param element タグ情報
         * @param root    タグがitem/containerのときtrue
         */
        fun create(element: Element, root: Boolean = false): Tag =
            create(element, if (root) "" else element.textContent)

        /**
         * インスタンス作成。
         *
         * @param element タグ情報
         * @param value   タグの値
         */
        private fun create(element: Element, value: String): Tag = Tag(
            element.tagName,
            value,
            element.attributes.asIterable().map { it.nodeName to it.nodeValue }.toMap()
        )
    }
}
