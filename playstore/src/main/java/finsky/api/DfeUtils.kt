package finsky.api

import android.util.Base64

import finsky.protos.nano.Messages

object DfeUtils {

    fun base64Encode(input: ByteArray): String {
        return Base64.encodeToString(input, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    fun getRootDoc(payload: Messages.Response.Payload?): Messages.DocV2? {
        if (null == payload) {
            return null
        }
        if (payload.searchResponse != null && payload.searchResponse.doc.isNotEmpty()) {
            return getRootDoc(payload.searchResponse.doc[0])
        } else if (payload.listResponse != null && payload.listResponse.doc.isNotEmpty()) {
            return getRootDoc(payload.listResponse.doc[0])
        }
        return null
    }

    private fun getRootDoc(doc: Messages.DocV2): Messages.DocV2? {
        if (isRootDoc(doc)) {
            return doc
        }
        for (child in doc.child) {
            val root = getRootDoc(child)
            if (null != root) {
                return root
            }
        }
        return null
    }

    private fun isRootDoc(doc: Messages.DocV2): Boolean {
        return doc.child.isNotEmpty() && doc.child[0].backendId == 3 && doc.child[0].docType == 1
    }
}