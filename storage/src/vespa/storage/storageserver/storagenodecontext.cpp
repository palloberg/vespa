// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storagenodecontext.h"

#include <vespa/storageframework/defaultimplementation/memory/prioritymemorylogic.h>

namespace storage {

using framework::defaultimplementation::AllocationLogic;
using framework::defaultimplementation::PriorityMemoryLogic;

StorageNodeContext::StorageNodeContext(ComponentRegister::UP compReg, framework::Clock::UP clock)
    : _componentRegister(std::move(compReg)),
      _clock(std::move(clock)),
      _threadPool(*_clock),
      _memoryLogic(new PriorityMemoryLogic(*_clock, 1024 * 1024 * 1024)),
      _memoryManager(AllocationLogic::UP(_memoryLogic))
{
    _componentRegister->setClock(*_clock);
    _componentRegister->setThreadPool(_threadPool);
    _componentRegister->setMemoryManager(_memoryManager);
}

void
StorageNodeContext::setMaximumMemoryUsage(uint64_t max)
{
    using storage::framework::defaultimplementation::PriorityMemoryLogic;
    dynamic_cast<PriorityMemoryLogic*>(_memoryLogic)
            ->setMaximumMemoryUsage(max);
}

} // storage
