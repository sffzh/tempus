package com.cappielloantonio.tempo.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.cappielloantonio.tempo.R;

import java.util.Objects;

import com.google.android.material.color.MaterialColors;

public final class AssetLinkUtil {
    public static final String SCHEME = "tempo";
    public static final String HOST_ASSET = "asset";

    public static final String TYPE_SONG = "song";
    public static final String TYPE_ALBUM = "album";
    public static final String TYPE_ARTIST = "artist";
    public static final String TYPE_PLAYLIST = "playlist";
    public static final String TYPE_GENRE = "genre";
    public static final String TYPE_YEAR = "year";

    private AssetLinkUtil() {
    }

    @Nullable
    public static AssetLink parse(@Nullable Intent intent) {
        if (intent == null) return null;
        return parse(intent.getData());
    }

    @Nullable
    public static AssetLink parse(@Nullable Uri uri) {
        if (uri == null) {
            return null;
        }

        if (!SCHEME.equalsIgnoreCase(uri.getScheme())) {
            return null;
        }

        String host = uri.getHost();
        if (!HOST_ASSET.equalsIgnoreCase(host)) {
            return null;
        }

        if (uri.getPathSegments().size() < 2) {
            return null;
        }

        String type = uri.getPathSegments().get(0);
        String id = uri.getPathSegments().get(1);
        if (TextUtils.isEmpty(type) || TextUtils.isEmpty(id)) {
            return null;
        }

        if (isUnsupportedType(type)) {
            return null;
        }

        return new AssetLink(type, id, uri);
    }

    public static boolean isUnsupportedType(@Nullable String type) {
        if (type == null) return true;
        //注意返回的是不支持类型
        return switch (type) {
            case TYPE_SONG, TYPE_ALBUM, TYPE_ARTIST, TYPE_PLAYLIST, TYPE_GENRE, TYPE_YEAR -> false;
            default -> true;
        };
    }

    @NonNull
    public static Uri buildUri(@NonNull String type, @NonNull String id) {
        return new Uri.Builder()
                .scheme(SCHEME)
                .authority(HOST_ASSET)
                .appendPath(type)
                .appendPath(id)
                .build();
    }

    @Nullable
    public static String buildLink(@Nullable String type, @Nullable String id) {
        if (TextUtils.isEmpty(type) || TextUtils.isEmpty(id) || isUnsupportedType(type)) {
            return null;
        }
        return buildUri(Objects.requireNonNull(type), Objects.requireNonNull(id)).toString();
    }

    @Nullable
    public static AssetLink buildAssetLink(@Nullable String type, @Nullable String id) {
        String link = buildLink(type, id);
        return parseLinkString(link);
    }

    @Nullable
    public static AssetLink parseLinkString(@Nullable String link) {
        if (TextUtils.isEmpty(link)) {
            return null;
        }
        return parse(Uri.parse(link));
    }

    public static void copyToClipboard(@NonNull Context context, @NonNull AssetLink assetLink) {
        ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null) {
            return;
        }
        ClipData clipData = ClipData.newPlainText(context.getString(R.string.asset_link_clipboard_label), assetLink.uri.toString());
        clipboardManager.setPrimaryClip(clipData);
    }

    @StringRes
    public static int getLabelRes(@NonNull String type) {
        return switch (type) {
            case TYPE_SONG -> R.string.asset_link_label_song;
            case TYPE_ALBUM -> R.string.asset_link_label_album;
            case TYPE_ARTIST -> R.string.asset_link_label_artist;
            case TYPE_PLAYLIST -> R.string.asset_link_label_playlist;
            case TYPE_GENRE -> R.string.asset_link_label_genre;
            case TYPE_YEAR -> R.string.asset_link_label_year;
            default -> R.string.asset_link_label_unknown;
        };
    }

    public static void applyLinkAppearance(@NonNull View view) {
        if (view instanceof TextView textView) {
            if (textView.getTag(R.id.tag_link_original_color) == null) {
                textView.setTag(R.id.tag_link_original_color, textView.getCurrentTextColor());
            }
            int accent = MaterialColors.getColor(view, R.attr.colorOnPrimary,
                    ContextCompat.getColor(view.getContext(), android.R.color.holo_blue_light));
            textView.setTextColor(accent);
        }
    }

    public static void clearLinkAppearance(@NonNull View view) {
        if (view instanceof TextView textView) {
            Object original = textView.getTag(R.id.tag_link_original_color);
            if (original instanceof Integer) {
                textView.setTextColor((Integer) original);
            } else {
                int defaultColor = MaterialColors.getColor(view, R.attr.colorOnSurface,
                        ContextCompat.getColor(view.getContext(), android.R.color.primary_text_light));
                textView.setTextColor(defaultColor);
            }
        }
    }

    public record AssetLink(String type, String id, Uri uri) {
            public AssetLink(@NonNull String type, @NonNull String id, @NonNull Uri uri) {
                this.type = type;
                this.id = id;
                this.uri = uri;
            }
        }
}
