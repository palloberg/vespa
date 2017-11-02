// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "make_document_bucket.h"

namespace document::test {

Bucket makeDocumentBucket(BucketId bucketId)
{
    return Bucket(BucketSpace::placeHolder(), bucketId);
}

}
