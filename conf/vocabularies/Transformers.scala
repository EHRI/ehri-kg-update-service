class Transformers {

  def generateVocabUri(vocabularyId: String, itemId: String): String = {
    s"$vocabularyId/$itemId"
  }

}