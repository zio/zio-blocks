const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO Blocks",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
         "reference/binding",
         "reference/chunk",
         "reference/context",
         "reference/docs",
         "reference/dynamic-value",
         "reference/formats",
         "reference/json",
         "reference/json-schema",
         "reference/optics",
         "reference/reflect",
         "reference/registers",
         "reference/schema",
         "reference/schema-evolution",
         "reference/syntax",
         "reference/typeid",
         "reference/validation"
      ]
    }
  ]
};

module.exports = sidebars;
