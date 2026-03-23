// This file is called via callSingle - should only execute once
// and return the same instance to all threads
function fn() {
  var instanceId = java.util.UUID.randomUUID().toString();
  karate.log('callSingle executed, instanceId:', instanceId);

  return {
    instanceId: instanceId,
    createdAt: new Date().toISOString(),
    data: { nested: { value: 'original' } },
    getId: function() { return instanceId; }
  };
}
