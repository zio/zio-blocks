const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO Blocks",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
         "reference/schema",
         "reference/reflect",
         "reference/binding",
         "reference/registers",
         "reference/typeid",
         "reference/modifier",
         "reference/dynamic-value",
         "reference/optics",
         "reference/dynamic-optic",
         "path-interpolator",
         "reference/chunk",
         "reference/validation",
         "reference/schema-evolution",
         "reference/context",
         "reference/docs",
         "reference/formats",
         "reference/json",
         "reference/json-schema",
         "reference/syntax",
      ]
    }
  ]
};

module.exports = sidebars;
