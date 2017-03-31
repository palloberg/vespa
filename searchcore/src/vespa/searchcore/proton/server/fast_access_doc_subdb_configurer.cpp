// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.fast_access_doc_subdb_configurer");

#include "fast_access_doc_subdb_configurer.h"
#include "i_attribute_writer_factory.h"
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/common/document_type_inspector.h>
#include <vespa/searchcore/proton/reprocessing/attribute_reprocessing_initializer.h>

using document::DocumentTypeRepo;
using search::index::Schema;

namespace proton {

using ARIConfig = AttributeReprocessingInitializer::Config;

void
FastAccessDocSubDBConfigurer::reconfigureFeedView(const FastAccessFeedView::SP &curr,
                                                  const Schema::SP &schema,
                                                  const DocumentTypeRepo::SP &repo,
                                                  const IAttributeWriter::SP &writer)
{
    _feedView.set(FastAccessFeedView::SP(new FastAccessFeedView(
            StoreOnlyFeedView::Context(curr->getSummaryAdapter(),
                    schema,
                    curr->getDocumentMetaStore(),
                    repo,
                    curr->getWriteService(),
                    curr->getLidReuseDelayer(),
                    curr->getCommitTimeTracker()),
            curr->getPersistentParams(),
            FastAccessFeedView::Context(writer,
                    curr->getDocIdLimit()))));
}

FastAccessDocSubDBConfigurer::FastAccessDocSubDBConfigurer(FeedViewVarHolder &feedView,
                                                           IAttributeWriterFactory::UP factory,
                                                           const vespalib::string &subDbName)
    : _feedView(feedView),
      _factory(std::move(factory)),
      _subDbName(subDbName)
{
}

IReprocessingInitializer::UP
FastAccessDocSubDBConfigurer::reconfigure(const DocumentDBConfig &newConfig,
                                          const DocumentDBConfig &oldConfig,
                                          const AttributeCollectionSpec &attrSpec)
{
    FastAccessFeedView::SP oldView = _feedView.get();
    IAttributeWriter::SP writer =
            _factory->create(oldView->getAttributeWriter(), attrSpec);
    reconfigureFeedView(oldView, newConfig.getSchemaSP(), newConfig.getDocumentTypeRepoSP(), writer);

    const document::DocumentType *newDocType = newConfig.getDocumentType();
    const document::DocumentType *oldDocType = oldConfig.getDocumentType();
    assert(newDocType != nullptr);
    assert(oldDocType != nullptr);
    DocumentTypeInspector inspector(*oldDocType, *newDocType);
    return std::make_unique<AttributeReprocessingInitializer>
        (ARIConfig(writer->getAttributeManager(), *newConfig.getSchemaSP()),
         ARIConfig(oldView->getAttributeWriter()->getAttributeManager(), *oldConfig.getSchemaSP()),
         inspector, _subDbName, attrSpec.getCurrentSerialNum());
}

} // namespace proton
