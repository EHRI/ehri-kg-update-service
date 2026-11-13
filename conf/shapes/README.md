# Shapes

Copies of the ShEx and SHACL shapes in `src/shapes/1.0.2` of the
[EHRI-KG-mapping repository](https://github.com/EHRI/ehri-kg-mapping) at `main@91d88bb`, updated to match this
service's mappings:

- **Activity types:** occupations, functions and acquisitions have `rico:hasActivityType` with the
  instances `activityTypes/occupation`, `activityTypes/function` and `activityTypes/acquisition`, which the
  KG doesn't yet define. Previously the shapes expected `rico:hasOrHadActivityType` with a class. Acquisitions are targeted as the objects of
  `rico:resultsOrResultedFrom`, not as every `rico:Activity`.
- **Holdings, renamed properties:** `ehri:biographicalHistory` and `ehri:archivalHistory` (for
  `rico:history`), `rico:recordResourceSourceOfInformation` (for `ehri:sources`), `rico:note` (for `ehri:notes`)
  and `ehri:physicalCharacteristicsAndTechnicalRequirements` (a typo).
- **Holdings, other changes:**
  - `rico:hasOrHadSubject` and creators in any EHRI authority set are allowed.
  - `rico:identifier` is an `xsd:string`.
  - Any number of `rico:date`s is allowed, one per description.
- **Holdings SHACL:** removed the duplicated property shapes that required optional properties, or required them
  with conflicting datatypes (`rico:conditionsOfUse`, `rico:date`, `rico:history`, `rico:hasRecordSetType`).
- **Institutions:** places use `rico:containsTransitive` and `rico:isContainedByTransitive` instead of
  `rico:containsOrContained` and `rico:isOrWasContainedBy`. A country contains regions or cities, not necessarily
  both.
- **Agents:** corporate bodies may also be families, in `ehri-fm`, and persons and corporate bodies may have any
  number of `rdfs:seeAlso`s.
- **Holders:** in the holdings SHACL, an institution may hold any number of units.

`terms`, `camps` and `ghettos` validate the batch conversion's separate files of units' subjects, which the service
doesn't produce, so no entity type uses them.

The `<type>Shapes` properties of `conf/config.properties` say which shapes validate each entity type's mapped data.
