package com.shisan.campuspro.feature.settings

import com.shisan.campuspro.core.model.CurrentPrivacyPolicyVersion
import java.net.URI

data class ComplianceDocumentMetadata(
    val version: String,
    val effectiveDate: String,
    val developerName: String,
    val contactEmail: String,
)

data class PersonalInformationCollectionItem(
    val id: String,
    val category: String,
    val information: String,
    val riskLevel: String,
    val purpose: String,
    val processingScene: String,
    val necessity: String,
    val storageLocation: String,
    val retentionPeriod: String,
    val externalTransmission: String,
    val controlMethod: String,
)

data class ThirdPartySharingItem(
    val id: String,
    val recipient: String,
    val domain: String,
    val information: String,
    val purpose: String,
    val processingMethod: String,
    val policyUrl: String?,
    val consentRequirement: String,
    val stopMethod: String,
)

data class LegalDocumentSection(val title: String, val body: String)

object ComplianceCatalog {
    val metadata = ComplianceDocumentMetadata(
        version = CurrentPrivacyPolicyVersion,
        effectiveDate = "2026年6月12日",
        developerName = "用户名菌",
        contactEmail = "schedule@shisanjike.icu",
    )

    val personalInformationItems = listOf(
        personalItem(
            id = "student_id",
            category = "登录身份",
            information = "学号",
            riskLevel = "个人信息",
            purpose = "登录教务系统并关联用户本人的教务数据",
            processingScene = "用户主动登录、重新认证或同步教务数据时",
            necessity = "登录与同步功能所必需",
            storageLocation = "应用私有加密凭证存储",
            retentionPeriod = "登录成功后保存，直至用户退出账号或清除应用数据",
            externalTransmission = "传输至吉首大学统一身份认证系统或教务系统",
            controlMethod = "停止登录、退出账号或清除应用数据",
        ),
        personalItem(
            id = "password",
            category = "身份鉴别",
            information = "教务系统密码",
            riskLevel = "高风险个人信息",
            purpose = "完成用户主动发起的统一身份认证或教务系统认证",
            processingScene = "用户输入密码并点击登录时",
            necessity = "账号密码登录方式所必需",
            storageLocation = "默认不保存；用户主动选择后保存在本机 EncryptedSharedPreferences 加密存储",
            retentionPeriod = "未选择保存时仅用于当前登录；选择保存时保留至退出账号、清除应用数据或卸载应用",
            externalTransmission = "根据登录方式传输至统一身份认证系统或教务系统",
            controlMethod = "不启用凭证保存，或退出账号、清除应用数据、卸载应用",
        ),
        personalItem(
            id = "session",
            category = "会话信息",
            information = "Cookie、登录会话标识",
            riskLevel = "高风险个人信息",
            purpose = "维持登录状态并完成后续数据同步",
            processingScene = "登录成功后访问教务功能时",
            necessity = "保持认证会话所必需",
            storageLocation = "应用进程内的 Cookie 存储",
            retentionPeriod = "当前会话期间，退出登录时清除",
            externalTransmission = "随请求发送至对应的学校认证或教务域名",
            controlMethod = "退出账号或停止使用应用",
        ),
        personalItem(
            id = "profile",
            category = "基本资料",
            information = "姓名、学号",
            riskLevel = "个人信息",
            purpose = "展示当前登录身份",
            processingScene = "登录成功和查看个人页面时",
            necessity = "账号身份展示所必需",
            storageLocation = "应用私有存储",
            retentionPeriod = "保留至退出账号或清除应用数据",
            externalTransmission = "除学校系统返回外，不向开发者服务器传输",
            controlMethod = "退出账号或清除应用数据",
        ),
        personalItem(
            id = "courses",
            category = "课程信息",
            information = "课程名称、教师、教室、周次、上课时间",
            riskLevel = "个人信息",
            purpose = "展示课表并提供课程提醒",
            processingScene = "用户同步和查看课表时",
            necessity = "课表功能所必需",
            storageLocation = "本机 Room 数据库",
            retentionPeriod = "保留至用户删除课表、退出并清除数据或卸载应用",
            externalTransmission = "同步时从教务系统获取，不向开发者服务器传输",
            controlMethod = "删除课表、退出并清除数据或卸载应用",
        ),
        personalItem(
            id = "exams",
            category = "考试信息",
            information = "考试科目、时间、地点",
            riskLevel = "个人信息",
            purpose = "展示考试安排",
            processingScene = "用户同步和查看考试时",
            necessity = "考试查询功能所必需",
            storageLocation = "本机 Room 数据库",
            retentionPeriod = "保留至退出并清除数据或卸载应用",
            externalTransmission = "同步时从教务系统获取，不向开发者服务器传输",
            controlMethod = "退出并清除数据或卸载应用",
        ),
        personalItem(
            id = "grades",
            category = "成绩信息",
            information = "课程成绩、绩点及相关学业结果",
            riskLevel = "高风险个人信息",
            purpose = "展示用户本人的成绩信息",
            processingScene = "用户主动同步和查看成绩时",
            necessity = "成绩查询功能所必需",
            storageLocation = "本机 Room 数据库",
            retentionPeriod = "保留至退出并清除数据或卸载应用",
            externalTransmission = "同步时从教务系统获取，不向开发者服务器传输",
            controlMethod = "退出并清除数据或卸载应用",
        ),
        personalItem(
            id = "custom_schedule",
            category = "用户输入",
            information = "自定义课程、课程备注及课表设置",
            riskLevel = "可能包含个人信息",
            purpose = "提供本地课表编辑与个性化展示",
            processingScene = "用户新增、编辑课程或调整课表时",
            necessity = "仅在使用自定义课表功能时需要",
            storageLocation = "本机 Room 数据库和应用偏好",
            retentionPeriod = "保留至用户删除相关内容或卸载应用",
            externalTransmission = "不向外部接收方传输",
            controlMethod = "删除课程、课表或清除应用数据",
        ),
        personalItem(
            id = "reminders",
            category = "提醒设置",
            information = "通知开关、提醒时间和课程提醒配置",
            riskLevel = "一般配置信息",
            purpose = "按用户选择发送本地课程提醒",
            processingScene = "用户启用或修改课程提醒时",
            necessity = "仅在使用提醒功能时需要",
            storageLocation = "本机应用偏好和系统任务调度",
            retentionPeriod = "保留至用户关闭提醒、清除数据或卸载应用",
            externalTransmission = "不向外部接收方传输",
            controlMethod = "关闭提醒、撤销通知权限或清除应用数据",
        ),
        personalItem(
            id = "updates",
            category = "更新信息",
            information = "应用版本、更新检查时间及请求所必需的网络信息",
            riskLevel = "一般技术信息",
            purpose = "检查是否存在新版本",
            processingScene = "用户点击检查更新时",
            necessity = "仅在使用在线更新检查时需要",
            storageLocation = "版本信息由系统提供，检查时间保存在本机偏好",
            retentionPeriod = "检查时间保留至清除应用数据或卸载应用",
            externalTransmission = "仅向配置的更新服务发送常规 HTTPS 请求，不包含教务数据",
            controlMethod = "不使用检查更新功能",
        ),
    )

    val privacyPolicySections = listOf(
        LegalDocumentSection("一、适用范围", "本政策适用于闪电课表 Android 客户端。闪电课表由个人开发者维护，是非吉首大学官方应用。"),
        LegalDocumentSection("二、我们处理的信息", "为提供登录、课表、考试、成绩、提醒和更新功能，应用会处理学号、教务密码、登录会话、个人资料、课程、考试、成绩、用户输入、提醒设置和必要的更新请求信息。详细内容以个人信息收集清单为准。"),
        LegalDocumentSection("三、账号与凭证", "教务密码仅用于用户主动发起的学校系统认证。凭证保存默认关闭；只有用户主动选择后，账号、密码和登录方式才会加密保存在应用私有空间，用于自动登录。"),
        LegalDocumentSection("四、外部接收方", "登录与同步时，必要信息通过 HTTPS 直接发送至吉首大学统一身份认证系统或教务系统。在线检查更新时，更新服务会收到完成 HTTPS 请求所必需的网络信息。应用不接入广告、画像或行为分析 SDK。"),
        LegalDocumentSection("五、存储与保护", "课程、考试、成绩及设置主要保存在本机应用私有空间。应用采用系统提供的加密存储保护用户主动保存的凭证，并限制数据仅用于对应功能。"),
        LegalDocumentSection("六、你的权利", "你可以拒绝或撤回同意、停止登录与同步、退出账号并清除凭证。撤回同意后在线功能停止，本地自行维护的课表仍可离线使用。"),
        LegalDocumentSection("七、未成年人", "本校内测试版本面向高校学生，不面向不满十四周岁的未成年人。发现相关信息时应停止处理并联系开发者删除。"),
        LegalDocumentSection("八、联系我们", "如需咨询、更正、删除或投诉，请通过本页所示联系邮箱联系开发者。开发者名称、邮箱和生效日期必须在正式发布前替换为真实信息。"),
    )

    val userAgreementSections = listOf(
        LegalDocumentSection("一、服务说明", "闪电课表用于在用户授权下连接学校系统并在本机整理课程、考试和成绩。本应用并非吉首大学官方产品，不代表学校作出任何承诺。"),
        LegalDocumentSection("二、账号授权", "你只能使用本人合法持有的学校账号，并授权应用代表你向学校系统发起登录和查询。不得使用他人账号或以任何方式绕过学校安全措施。"),
        LegalDocumentSection("三、使用规范", "不得利用应用实施高频访问、攻击、破坏、干扰学校系统或其他违法违规行为。学校系统规则与学校正式通知具有优先效力。"),
        LegalDocumentSection("四、数据准确性", "课表、考试、成绩和提醒仅供参考，最终信息以学校教务系统及学校正式通知为准。用户应自行核对重要安排。"),
        LegalDocumentSection("五、服务变更", "学校接口、认证方式或网络环境变化可能导致部分功能临时不可用。开发者可为安全、合规或维护需要调整、暂停相关功能。"),
        LegalDocumentSection("六、知识产权", "应用自身代码、界面和文档依法受到保护；第三方开源组件按各自许可证使用，具体信息可在开源许可页面查看。"),
        LegalDocumentSection("七、责任边界", "开发者将采取合理措施保护数据安全，但依法不能免除因故意或重大过失造成损害所应承担的责任。"),
        LegalDocumentSection("八、联系与反馈", "使用过程中如有疑问或发现安全问题，请通过关于我们页面所示联系邮箱反馈。"),
    )

    fun thirdPartySharingItems(updateManifestUrl: String): List<ThirdPartySharingItem> = buildList {
        add(
            ThirdPartySharingItem(
                id = "jsu_authserver",
                recipient = "吉首大学统一身份认证系统",
                domain = "authserver.jsu.edu.cn",
                information = "学号、经学校页面规则加密后的认证数据、会话参数",
                purpose = "完成用户主动选择的门户统一认证",
                processingMethod = "用户点击登录后，通过 HTTPS 直接发送至学校系统",
                policyUrl = "https://authserver.jsu.edu.cn/authserver/login",
                consentRequirement = "登录前由用户主动确认并发起",
                stopMethod = "不使用门户登录，或退出当前账号",
            ),
        )
        add(
            ThirdPartySharingItem(
                id = "jsu_jwxt",
                recipient = "吉首大学教务系统",
                domain = "jwxt.jsu.edu.cn",
                information = "学号、密码或认证会话，以及用户请求查询的课程、考试和成绩数据",
                purpose = "完成教务系统登录和用户主动发起的数据同步",
                processingMethod = "通过 HTTPS 与学校教务系统直接通信",
                policyUrl = "https://jwxt.jsu.edu.cn",
                consentRequirement = "登录和同步前由用户主动确认并发起",
                stopMethod = "停止登录或同步，并退出当前账号",
            ),
        )
        updateServiceItem(updateManifestUrl)?.let(::add)
    }

    fun updateServiceItem(updateManifestUrl: String): ThirdPartySharingItem? {
        val uri = runCatching { URI(updateManifestUrl) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || host == "127.0.0.1" || host == "localhost") {
            return null
        }
        return ThirdPartySharingItem(
            id = "update_service",
            recipient = "闪电课表更新服务提供方",
            domain = host,
            information = "应用版本及 HTTPS 请求所必需的网络信息",
            purpose = "查询新版本和获取更新说明",
            processingMethod = "仅在用户主动检查更新时通过 HTTPS 请求更新清单",
            policyUrl = null,
            consentRequirement = "由用户主动触发检查更新",
            stopMethod = "不使用检查更新功能",
        )
    }

    private fun personalItem(
        id: String,
        category: String,
        information: String,
        riskLevel: String,
        purpose: String,
        processingScene: String,
        necessity: String,
        storageLocation: String,
        retentionPeriod: String,
        externalTransmission: String,
        controlMethod: String,
    ) = PersonalInformationCollectionItem(
        id = id,
        category = category,
        information = information,
        riskLevel = riskLevel,
        purpose = purpose,
        processingScene = processingScene,
        necessity = necessity,
        storageLocation = storageLocation,
        retentionPeriod = retentionPeriod,
        externalTransmission = externalTransmission,
        controlMethod = controlMethod,
    )
}

data class PersonalInformationTableRow(val label: String, val value: String)

fun personalInformationTableRows(item: PersonalInformationCollectionItem): List<PersonalInformationTableRow> = listOf(
    PersonalInformationTableRow("具体信息", item.information),
    PersonalInformationTableRow("处理目的", item.purpose),
    PersonalInformationTableRow("处理场景", item.processingScene),
    PersonalInformationTableRow("是否必要", item.necessity),
    PersonalInformationTableRow("存储位置", item.storageLocation),
    PersonalInformationTableRow("保存期限", item.retentionPeriod),
    PersonalInformationTableRow("外部传输", item.externalTransmission),
    PersonalInformationTableRow("关闭或删除", item.controlMethod),
)
