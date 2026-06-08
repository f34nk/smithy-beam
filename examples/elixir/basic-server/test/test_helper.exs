support_dir = Path.join([__DIR__, "support"])

for file <- Path.wildcard(Path.join(support_dir, "*.ex")) do
  Code.require_file(file)
end

:ok = BasicServiceServer.init_handlers()

ExUnit.start()
