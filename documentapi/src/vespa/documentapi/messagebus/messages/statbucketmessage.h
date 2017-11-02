// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentmessage.h"
#include <vespa/document/bucket/bucket.h>

namespace documentapi {

class StatBucketMessage : public DocumentMessage {
private:
    document::Bucket _bucket;
    string        _documentSelection;

protected:
    DocumentReply::UP doCreateReply() const override;

public:
    /**
     * Constructs a new message with no content.
     */
    StatBucketMessage();

    /**
     * Constructs a new message with initial content.
     *
     * @param bucket The bucket whose list to retrieve.
     */
    StatBucketMessage(document::Bucket bucket, const string& documentSelection);

    ~StatBucketMessage();

    /**
     * Returns the bucket to stat.
     *
     * @return The bucket id.
     */
    document::Bucket getBucket() const { return _bucket; }

    /**
     * Set the bucket to stat.
     *
     * @param id The identifier to set.
     */
    void setBucket(document::Bucket bucket) { _bucket = bucket; };

    /**
     * Returns the document selection used to filter the documents
     * returned.
     *
     * @return The selection string.
     */
    const string &getDocumentSelection() const { return _documentSelection; };

    /**
     * Sets the document selection used to filter the documents returned.
     *
     * @param value The selection string to set.
     */
    void setDocumentSelection(const string &value) { _documentSelection = value; };
    uint32_t getType() const override;
    string toString() const override { return "statbucketmessage"; }
};

}
