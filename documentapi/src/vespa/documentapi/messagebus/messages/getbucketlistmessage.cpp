// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "getbucketlistmessage.h"
#include "getbucketlistreply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>

namespace documentapi {

GetBucketListMessage::GetBucketListMessage(const document::Bucket &bucket) :
    DocumentMessage(),
    _bucket(bucket)
{
    // empty
}

DocumentReply::UP
GetBucketListMessage::doCreateReply() const
{
    return DocumentReply::UP(new GetBucketListReply());
}

uint32_t
GetBucketListMessage::getType() const
{
    return DocumentProtocol::MESSAGE_GETBUCKETLIST;
}

}
