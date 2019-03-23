/*
 * Copyright (c) 2016 大前良介 (OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.android.upnp.cds;

import net.mm2d.upnp.util.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * シンプルなXML情報を表現するクラス。
 *
 * <p>親要素とその子要素、以外に入れ子関係を持たない
 * シンプルなXML構造を表現する。
 *
 * @author <a href="mailto:ryo@mm2d.net">大前良介 (OHMAE Ryosuke)</a>
 */
class TagMap {
    /**
     * XMLのタグ情報。
     *
     * <p>タグ名をKeyとして、TagのListを保持する。
     * 同一のタグが複数ある場合はListに出現順に格納する。
     */
    private final Map<String, List<Tag>> mMap;

    /**
     * インスタンス作成。
     */
    TagMap() {
        mMap = new LinkedHashMap<>();
    }

    /**
     * タグ情報を格納する。
     *
     * @param name タグ名
     * @param tag  格納するタグ情報
     */
    void putTag(@Nonnull String name, @Nonnull Tag tag) {
        mMap.computeIfAbsent(name, k -> new ArrayList<>(1)).add(tag);
    }

    /**
     * XPATH風の指定で示された値を返す。
     *
     * <p>XPATHはitemもしくはcontainerをルートとして指定する。
     * 名前空間はシンボルをそのまま記述する。
     * 例えば'item@id'及び'container@id'はともに'@id'を指定する。
     * 'item/dc:title'であれば'dc:title'を指定する。
     *
     * <p>複数同一のタグがあった場合は最初に現れた要素の値を返す。
     * {@link #getValue(String, int)}で第二引数に0を指定するのと等価
     *
     * @param xpath パラメータの位置を表現するXPATH風の指定
     * @return 指定された値。見つからない場合はnull
     * @see #getValue(String, int)
     */
    @Nullable
    String getValue(@Nonnull String xpath) {
        return getValue(xpath, 0);
    }

    /**
     * XPATH風の指定で示された値を返す。
     *
     * <p>XPATHはitemもしくはcontainerをルートとして指定する。
     * 名前空間はシンボルをそのまま記述する。
     * 例えば'item@id'及び'container@id'はともに'@id'を指定する。
     * 'item/dc:title'であれば'dc:title'を指定する。
     *
     * <p>複数同一のタグがあった場合は現れた順にインデックスが付けられる。
     * 属性値はタグと同じインデックスに格納される。
     * 例えば、aというタグが複数あるが、
     * 一つ目に現れたタグにはbという属性はなく、
     * 二つ目に現れたタグにbという属性がある状態で、
     * 'a@b'を指定したとすると、インデックス0の値はnullとなる。
     * 二つ目に現れた属性bの値を取り出すには、インデックス1の値を取り出す必要がある。
     *
     * @param xpath パラメータの位置を表現するXPATH風の指定
     * @param index インデックス値
     * @return 指定された値。見つからない場合はnull
     */
    @Nullable
    String getValue(@Nonnull String xpath, int index) {
        final int pos = xpath.indexOf('@');
        if (pos < 0) {
            return getValue(xpath, null, index);
        }
        final String tagName = xpath.substring(0, pos);
        final String attrName = xpath.substring(pos + 1);
        return getValue(tagName, attrName, index);
    }

    /**
     * タグ名と属性名を指定して値を取り出す。
     *
     * <p>複数の同一タグがある場合は最初に現れたタグの情報を返す。
     * {@link #getValue(String, String, int)}の第三引数に0を指定したものと等価。
     *
     * @param tagName  タグ名
     * @param attrName 属性名、タグの値を取得するときはnullを指定する。
     * @return 指定された値。見つからない場合はnull
     * @see #getValue(String, String, int)
     */
    @Nullable
    String getValue(@Nullable String tagName, @Nullable String attrName) {
        return getValue(tagName, attrName, 0);
    }

    /**
     * タグ名と属性名を指定して値を取り出す。
     *
     * <p>複数同一のタグがあった場合は現れた順にインデックスが付けられる。
     * 属性値はタグと同じインデックスに格納される。
     *
     * @param tagName  タグ名、ルート要素を指定する場合はnullもしくは空文字列
     * @param attrName 属性名、タグの値を取得するときはnullを指定する。
     * @param index    インデックス値
     * @return 指定された値。見つからない場合はnull
     */
    @Nullable
    String getValue(@Nullable String tagName, @Nullable String attrName, int index) {
        final Tag tag = getTag(tagName, index);
        if (tag == null) {
            return null;
        }
        if (TextUtils.isEmpty(attrName)) {
            return tag.getValue();
        }
        return tag.getAttribute(attrName);
    }

    /**
     * 指定したタグ名のTagインスタンスを返す。
     *
     * <p>複数同一タグが存在した場合は最初に現れたタグ。
     *
     * @param tagName タグ名、ルート要素を指定する場合はnullもしくは空文字列
     * @return Tagインスタンス、見つからない場合はnull
     */
    @Nullable
    Tag getTag(@Nullable String tagName) {
        return getTag(tagName, 0);
    }

    /**
     * 指定したタグ名、インデックスのTagインスタンスを返す。
     *
     * @param tagName タグ名、ルート要素を指定する場合はnullもしくは空文字列
     * @param index   インデックス値
     * @return Tagインスタンス、見つからない場合はnull
     */
    @Nullable
    Tag getTag(@Nullable String tagName, int index) {
        final List<Tag> list = getTagList(tagName);
        if (list == null || list.size() <= index) {
            return null;
        }
        return list.get(index);
    }

    /**
     * 指定したタグ名のTagインスタンスリストを返す。
     *
     * @param tagName タグ名、ルート要素を指定する場合はnullもしくは空文字列
     * @return Tagインスタンスリスト
     */
    @Nullable
    List<Tag> getTagList(@Nullable String tagName) {
        if (tagName == null) {
            tagName = "";
        }
        return mMap.get(tagName);
    }

    @Override
    @Nonnull
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (final Entry<String, List<Tag>> entry : mMap.entrySet()) {
            final List<Tag> tags = entry.getValue();
            for (int i = 0; i < tags.size(); i++) {
                final Tag tag = tags.get(i);
                sb.append(entry.getKey());
                if (tags.size() == 1) {
                    sb.append(" => ");
                } else {
                    sb.append("[");
                    sb.append(String.valueOf(i));
                    sb.append("] => ");
                }
                sb.append(tag.getValue());
                sb.append("\n");
                final Map<String, String> attrs = tag.getAttributes();
                for (final Entry<String, String> e : attrs.entrySet()) {
                    sb.append("      @");
                    sb.append(e.getKey());
                    sb.append(" => ");
                    sb.append(e.getValue());
                    sb.append("\n");
                }
            }
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return mMap.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TagMap)) {
            return false;
        }
        final TagMap obj = (TagMap) o;
        return mMap.equals(obj.mMap);
    }
}
