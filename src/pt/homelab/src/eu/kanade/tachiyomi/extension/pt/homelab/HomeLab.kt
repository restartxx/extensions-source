package eu.kanade.tachiyomi.extension.pt.homelab

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class HomeLab : HttpSource() {

    override val name = "HomeLab (Termux)"
    // IMPORTANTE: Se for rodar no emulador do PC use 10.0.2.2
    // Se for no celular com extensão instalada, use o IP da VPN ou 127.0.0.1 se usar port forward
    override val baseUrl = "http://127.0.0.1:8000" 
    override val lang = "pt"
    override val supportsLatest = true

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/list", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = json.parseToJsonElement(response.body.string()).jsonArray
        val mangas = result.map { element ->
            val obj = element.jsonObject
            SManga.create().apply {
                title = obj["title"]!!.jsonPrimitive.content
                // O ID vem do Python, guardamos na URL
                url = "/api/chapters/" + obj["id"]!!.jsonPrimitive.content 
                thumbnail_url = obj["cover_url"]?.jsonPrimitive?.content ?: ""
                initialized = true
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)
    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
         // Se seu python não tiver busca, retorna a lista completa e filtra na marra (lento mas funciona)
         return GET("$baseUrl/api/list", headers)
    }
    
    override fun searchMangaParse(response: Response): MangasPage {
        // Filtragem burra no client-side caso a API não suporte ?q=
        val original = popularMangaParse(response)
        // Se você implementou ?q= no python, use a lógica normal. 
        // Se não, o Mihon vai mostrar tudo.
        return original 
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        description = "HomeLab Server"
        status = SManga.ONGOING
    }

    override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = json.parseToJsonElement(response.body.string()).jsonArray
        return result.map { element ->
            val obj = element.jsonObject
            SChapter.create().apply {
                name = obj["title"]!!.jsonPrimitive.content
                val id = obj["id"]!!.jsonPrimitive.content
                // Assume que a API de páginas é /api/pages/MANGA_ID/CHAPTER_ID
                // A URL do capitulo atual é .../api/chapters/MANGA_ID
                // Trocamos "chapters" por "pages" e adicionamos o ID do capitulo
                url = response.request.url.toString().replace("/chapters/", "/pages/") + "/$id"
                date_upload = 0L
            }
        }.reversed()
    }

    override fun pageListRequest(chapter: SChapter) = GET(chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val result = json.parseToJsonElement(response.body.string()).jsonArray
        return result.mapIndexed { index, element ->
            val path = element.jsonPrimitive.content
            val fullUrl = if (path.startsWith("http")) path else "$baseUrl$path"
            Page(index, "", fullUrl)
        }
    }

    override fun imageUrlParse(response: Response) = ""
}

