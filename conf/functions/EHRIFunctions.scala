class EHRIFunctions {

  // The path, relative to http://lod.ehri-project-test.eu/vocabularies/, of a historical agent or concept: its
  // Portal id's vocabulary part, with '-' for '_', then the rest of the id, e.g. ehri_pers-000001 -> ehri-pers/000001.
  // An id without a '-' gives its whole id as the vocabulary part, and nothing after the '/'.
  // Keep in step with vocabularyPath in the service code.
  def vocabularyPath(id: String): String = {
    val (vocabulary, rest) = id.span(_ != '-')
    vocabulary.replace('_', '-') + "/" + rest.drop(1)
  }

  // Paths of an entity's other and parallel names. Some names have control characters, which IRIs can't, e.g. the
  // MARC non-sorting markers U+0098 and U+009C around "von", so they are left out; line breaks and tabs become spaces.
  def entityOtherNamePath(id: String, name: String): String = s"$id/otherNames/${iriName(name)}"
  def entityParallelNamePath(id: String, name: String): String = s"$id/parallelNames/${iriName(name)}"
  private def iriName(name: String): String = name.replaceAll("[\\t\\n\\r]", " ").replaceAll("\\p{Cc}", "")

  // Paths of the nodes belonging to a historical agent
  def otherNamePath(id: String, name: String): String = entityOtherNamePath(vocabularyPath(id), name)
  def parallelNamePath(id: String, name: String): String = entityParallelNamePath(vocabularyPath(id), name)
  def legalStatusPath(id: String): String = s"${vocabularyPath(id)}/legalStatuses/1"
  def occupationPath(id: String): String = s"${vocabularyPath(id)}/occupations/1"
  def functionPath(id: String, index: String): String = s"${vocabularyPath(id)}/functions/${index.toInt + 1}"
  def mandatePath(id: String, index: String): String = s"${vocabularyPath(id)}/mandates/${index.toInt + 1}"

  // Kinds of item an access point can link to. The service leaves out access points to agents and concepts whose
  // authority sets and vocabularies aren't in the KG before mapping (see KgSets in the service code).
  def isAgent(itemType: String): Boolean = itemType == "HistoricalAgent"
  def isAgentOrConcept(itemType: String): Boolean = itemType == "HistoricalAgent" || itemType == "CvocConcept"

}
