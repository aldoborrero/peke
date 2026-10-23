{
  flake,
  inputs,
  pkgs,
  ...
}:
let
  mod = inputs.treefmt-nix.lib.evalModule pkgs {
    projectRootFile = "flake.nix";

    programs = {
      # nix: deadnix strips dead code, then nixfmt formats
      deadnix.enable = true;
      deadnix.no-lambda-pattern-names = true;
      deadnix.priority = 1;
      nixfmt.enable = true;
      nixfmt.priority = 2;

      # Kotlin and Gradle scripts: the same ktlint as the pre-commit hook; reads .editorconfig
      ktlint.enable = true;

      # GitHub workflows
      actionlint.enable = true;
    };

    settings.excludes = [
      # Third-party files kept as published: spec-kit and the agent skills
      ".specify/**"
      ".claude/**"
      # Generated Room schemas
      "app/schemas/**"
    ];
  };
  wrapper = mod.config.build.wrapper;
in
wrapper
// {
  passthru = wrapper.passthru // {
    tests.check = mod.config.build.check flake;
  };
}
