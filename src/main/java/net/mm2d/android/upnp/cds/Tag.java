/*
 * Copyright (c) 2016 大前良介 (OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.android.upnp.cds;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * シンプルなXMLのタグ情報を表現するクラス
 *
 * <p>Elementのままでは情報の参照コストが高いため、
 * よりシンプルな構造に格納するためのクラス。
 * CdsObjectのXMLのようにElementが入れ子になることのない
 * タグ＋値、属性＋値の情報を表現できれば十分なものを表現するのに使用する。
 * 入れ子関係を持つXMLは表現できない。
 *
 * @author <a href="mailto:ryo@mm2d.net">大前良介(OHMAE Ryosuke)</a>
 */
public class Tag {
    @Nonnull
    private final String mName;
    @Nonnull
    private final String mValue;
    @Nonnull
    private final Map<String, String> mAttribute;

    /**
     * インスタンス作成。
     *
     * パッケージ外でのインスタンス化禁止
     *
     * @param element タグ情報
     */
    Tag(@Nonnull Element element) {
        this(element, false);
    }

    /**
     * インスタンス作成。
     *
     * パッケージ外でのインスタンス化禁止
     *
     * @param element タグ情報
     * @param root    タグがitem/containerのときtrue
     */
    Tag(@Nonnull Element element, boolean root) {
        this(element, root ? "" : element.getTextContent());
    }

    /**
     * インスタンス作成。
     *
     * @param element タグ情報
     * @param value   タグの値
     */
    private Tag(@Nonnull Element element, @Nonnull String value) {
        mName = element.getTagName();
        mValue = value;
        final NamedNodeMap attributes = element.getAttributes();
        final int size = attributes.getLength();
        if (size == 0) {
            mAttribute = Collections.emptyMap();
            return;
        }
        mAttribute = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) {
            final Node attr = attributes.item(i);
            mAttribute.put(attr.getNodeName(), attr.getNodeValue());
        }
    }

    /**
     * タグ名を返す。
     *
     * @return タグ名
     */
    @Nonnull
    public String getName() {
        return mName;
    }

    /**
     * タグの値を返す。
     *
     * @return タグの値
     */
    @Nonnull
    public String getValue() {
        return mValue;
    }

    /**
     * 属性値を返す。
     *
     * @param name 属性名
     * @return 属性値、見つからない場合null
     */
    @Nullable
    public String getAttribute(@Nullable String name) {
        return mAttribute.get(name);
    }

    /**
     * 属性値を格納したMapを返す。
     *
     * @return 属性値を格納したUnmodifiable Map
     */
    @Nonnull
    public Map<String, String> getAttributes() {
        if (mAttribute.size() == 0) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(mAttribute);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(mValue);
        for (final Entry<String, String> entry : mAttribute.entrySet()) {
            sb.append("\n");
            sb.append("@");
            sb.append(entry.getKey());
            sb.append(" => ");
            sb.append(entry.getValue());
        }
        return sb.toString();
    }
}
