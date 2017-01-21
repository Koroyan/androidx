/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.support.navigation.app.nav;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.support.annotation.XmlRes;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.util.Xml;

import com.android.support.navigation.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Class which translates a navigation XML file into a {@link NavGraph}
 */
public class NavInflater {
    private static final String TAG_ROOT = "navigation";
    private static final String TAG_DESTINATION = "destination";
    private static final String TAG_ARGUMENT = "default-argument";
    private static final String TAG_ACTION = "action";

    private static final ThreadLocal<TypedValue> sTmpValue = new ThreadLocal<>();

    private Context mContext;
    private NavigatorProvider mNavigatorProvider;

    public NavInflater(Context c, NavigatorProvider navigatorProvider) {
        mContext = c;
        mNavigatorProvider = navigatorProvider;
    }

    /**
     * Retrieve a Navigator with the given name from the {@link NavigatorProvider} used to
     * construct this class.
     *
     * @param name
     * @return
     */
    public Navigator getNavigator(String name) {
        return mNavigatorProvider.getNavigator(name);
    }

    /**
     * Inflate a NavGraph from the given XML resource id.
     *
     * @param navres
     * @return
     */
    public NavGraph inflate(@XmlRes int navres) {
        Resources res = mContext.getResources();
        XmlResourceParser parser = res.getXml(navres);
        NavGraph graph = new NavGraph();

        final AttributeSet attrs = Xml.asAttributeSet(parser);
        try {
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
                // Empty loop
            }
            if (type != XmlPullParser.START_TAG) {
                throw new XmlPullParserException("No start tag found");
            }

            if (!TAG_ROOT.equals(parser.getName())) {
                throw new XmlPullParserException("Expected root element <" + TAG_ROOT + ">");
            }

            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (type != XmlPullParser.START_TAG) {
                    continue;
                }

                if (TAG_DESTINATION.equals(parser.getName())) {
                    final NavDestination dest;
                    try {
                        dest = inflateDestination(res, parser, attrs);
                    } catch (Exception e) {
                        throw new RuntimeException("Exception inflating "
                                + res.getResourceName(navres) + " line "
                                + parser.getLineNumber(), e);
                    }
                    graph.addDestination(dest);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            parser.close();
        }
        return graph;
    }

    private NavDestination inflateDestination(Resources res, XmlResourceParser parser,
                                              AttributeSet attrs)
            throws XmlPullParserException, IOException {
        final NavDestination dest = new NavDestination();

        dest.onInflate(mContext, attrs, this);

        final int innerDepth = parser.getDepth() + 1;
        int type;
        int depth;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth
                || type != XmlPullParser.END_TAG)) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }

            if (depth > innerDepth) {
                continue;
            }

            final String name = parser.getName();
            if (TAG_ARGUMENT.equals(name)) {
                inflateArgument(res, dest, attrs);
            } else if (TAG_ACTION.equals(name)) {
                inflateAction(res, dest, attrs);
            }
        }

        return dest;
    }

    private void inflateArgument(Resources res, NavDestination dest, AttributeSet attrs)
            throws XmlPullParserException {
        final TypedArray a = res.obtainAttributes(attrs, R.styleable.NavArgument);
        String name = a.getString(R.styleable.NavArgument_argname);

        TypedValue value = sTmpValue.get();
        if (value == null) {
            value = new TypedValue();
            sTmpValue.set(value);
        }
        a.getValue(R.styleable.NavArgument_argvalue, value);
        switch (value.type) {
            case TypedValue.TYPE_STRING:
                dest.getDefaultArguments().putString(name, value.string.toString());
                break;
            case TypedValue.TYPE_DIMENSION:
                dest.getDefaultArguments().putInt(name,
                        (int) value.getDimension(res.getDisplayMetrics()));
                break;
            case TypedValue.TYPE_FLOAT:
                dest.getDefaultArguments().putFloat(name, value.getFloat());
                break;
            case TypedValue.TYPE_REFERENCE:
                dest.getDefaultArguments().putInt(name, value.data);
                break;
            default:
                if (value.type >= TypedValue.TYPE_FIRST_INT
                        && value.type <= TypedValue.TYPE_LAST_INT) {
                    dest.getDefaultArguments().putInt(name, value.data);
                } else {
                    throw new XmlPullParserException("unsupported argument type " + value.type);
                }
        }
        a.recycle();
    }

    private void inflateAction(Resources res, NavDestination dest, AttributeSet attrs) {
        final TypedArray a = res.obtainAttributes(attrs, R.styleable.NavAction);
        final int id = a.getResourceId(R.styleable.NavAction_android_id, 0);
        final int destId = a.getResourceId(R.styleable.NavAction_destination, 0);
        dest.putActionDestination(id, destId);
        a.recycle();
    }
}
