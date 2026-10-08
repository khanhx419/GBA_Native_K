package com.gba.nativeemu.scanner

object GbaItemDatabase {

    data class ItemInfo(
        val id: Int,
        val nameEn: String,
        val nameVi: String,
        val category: String
    ) {
        val displayName: String get() = "$nameEn ($nameVi)"
        val hexId: String get() = "0x" + id.toString(16).uppercase().padStart(4, '0')
    }

    private val items = listOf(
        // Pokéballs (1..12)
        ItemInfo(1, "Master Ball", "Bóng Thần Kỳ - Bắt 100%", "Poké Ball"),
        ItemInfo(2, "Ultra Ball", "Bóng Siêu Cấp", "Poké Ball"),
        ItemInfo(3, "Great Ball", "Bóng Cao Cấp", "Poké Ball"),
        ItemInfo(4, "Poké Ball", "Bóng Tiêu Chuẩn", "Poké Ball"),
        ItemInfo(5, "Safari Ball", "Bóng Safari", "Poké Ball"),
        ItemInfo(6, "Net Ball", "Bóng Lưới (Bắt Nước/Bọ)", "Poké Ball"),
        ItemInfo(7, "Dive Ball", "Bóng Lặn (Bắt Dưới Nước)", "Poké Ball"),
        ItemInfo(8, "Nest Ball", "Bóng Bắt Cấp Thấp", "Poké Ball"),
        ItemInfo(9, "Repeat Ball", "Bóng Bắt Lại", "Poké Ball"),
        ItemInfo(10, "Timer Ball", "Bóng Thời Gian", "Poké Ball"),
        ItemInfo(11, "Luxury Ball", "Bóng Thân Thiện", "Poké Ball"),
        ItemInfo(12, "Premier Ball", "Bóng Kỷ Niệm", "Poké Ball"),

        // Hồi phục & Chữa trị (13..37)
        ItemInfo(13, "Potion", "Thuốc Hồi Phục 20 HP", "Hồi phục"),
        ItemInfo(14, "Antidote", "Thuốc Giải Độc", "Chữa trị"),
        ItemInfo(15, "Burn Heal", "Thuốc Trị Bỏng", "Chữa trị"),
        ItemInfo(16, "Ice Heal", "Thuốc Trị Đóng Băng", "Chữa trị"),
        ItemInfo(17, "Awakening", "Thuốc Đánh Thức", "Chữa trị"),
        ItemInfo(18, "Parlyz Heal", "Thuốc Trị Tê Liệt", "Chữa trị"),
        ItemInfo(19, "Full Restore", "Hồi Phục Đầy Đủ (Max HP + Trạng thái)", "Hồi phục"),
        ItemInfo(20, "Max Potion", "Thuốc Đầy Máu (Full HP)", "Hồi phục"),
        ItemInfo(21, "Hyper Potion", "Thuốc Hồi Phục 200 HP", "Hồi phục"),
        ItemInfo(22, "Super Potion", "Thuốc Hồi Phục 50 HP", "Hồi phục"),
        ItemInfo(23, "Full Heal", "Chữa Mọi Trạng Thái", "Chữa trị"),
        ItemInfo(24, "Revive", "Hồi Sinh 50% HP", "Hồi phục"),
        ItemInfo(25, "Max Revive", "Hồi Sinh 100% HP", "Hồi phục"),
        ItemInfo(26, "Fresh Water", "Nước Tinh Khiết 50 HP", "Hồi phục"),
        ItemInfo(27, "Soda Pop", "Nước Ngọt 60 HP", "Hồi phục"),
        ItemInfo(28, "Lemonade", "Nước Chanh 80 HP", "Hồi phục"),
        ItemInfo(29, "Moomoo Milk", "Sữa Moomoo 100 HP", "Hồi phục"),
        ItemInfo(30, "Energy Powder", "Bột Năng Lượng", "Hồi phục"),
        ItemInfo(31, "Energy Root", "Rễ Năng Lượng", "Hồi phục"),
        ItemInfo(32, "Heal Powder", "Bột Chữa Trị", "Chữa trị"),
        ItemInfo(33, "Revival Herb", "Thảo Dược Hồi Sinh", "Hồi phục"),
        ItemInfo(34, "Ether", "Hồi 10 PP", "Hồi phục"),
        ItemInfo(35, "Max Ether", "Hồi Đầy PP 1 Chiêu", "Hồi phục"),
        ItemInfo(36, "Elixir", "Hồi 10 PP Tất Cả Chiêu", "Hồi phục"),
        ItemInfo(37, "Max Elixir", "Hồi Đầy PP Tất Cả Chiêu", "Hồi phục"),
        ItemInfo(38, "Lava Cookie", "Bánh Dung Nham", "Chữa trị"),

        // Vitamin & Kẹo Tăng Cấp (63..71)
        ItemInfo(63, "HP Up", "Tăng Điểm HP Gốc", "Vitamin"),
        ItemInfo(64, "Protein", "Tăng Tấn Công Gốc (Attack)", "Vitamin"),
        ItemInfo(65, "Iron", "Tăng Phòng Thủ Gốc (Defense)", "Vitamin"),
        ItemInfo(66, "Carbos", "Tăng Tốc Độ Gốc (Speed)", "Vitamin"),
        ItemInfo(67, "Calcium", "Tăng Tấn Công Đặc Biệt (Sp. Atk)", "Vitamin"),
        ItemInfo(68, "Rare Candy", "Kẹo Tăng 1 Cấp (Level Up)", "Kẹo"),
        ItemInfo(69, "PP Up", "Tăng Giới Hạn PP", "Vitamin"),
        ItemInfo(70, "Zinc", "Tăng Phòng Thủ Đặc Biệt (Sp. Def)", "Vitamin"),
        ItemInfo(71, "PP Max", "Tăng Tối Đa Giới Hạn PP", "Vitamin"),

        // Đá tiến hóa (93..98)
        ItemInfo(93, "Sun Stone", "Đá Mặt Trời", "Tiến hóa"),
        ItemInfo(94, "Moon Stone", "Đá Mặt Trăng", "Tiến hóa"),
        ItemInfo(95, "Fire Stone", "Đá Lửa", "Tiến hóa"),
        ItemInfo(96, "Thunder Stone", "Đá Sấm Sét", "Tiến hóa"),
        ItemInfo(97, "Water Stone", "Đá Nước", "Tiến hóa"),
        ItemInfo(98, "Leaf Stone", "Đá Lá Cây", "Tiến hóa"),

        // Đồ quý & Bán lấy tiền (103..115)
        ItemInfo(103, "Tiny Mushroom", "Nấm Nhỏ", "Giá trị"),
        ItemInfo(104, "Big Mushroom", "Nấm Lớn", "Giá trị"),
        ItemInfo(106, "Pearl", "Ngọc Trai", "Giá trị"),
        ItemInfo(107, "Big Pearl", "Ngọc Trai Lớn", "Giá trị"),
        ItemInfo(108, "Stardust", "Bụi Sao", "Giá trị"),
        ItemInfo(109, "Star Piece", "Mảnh Sao", "Giá trị"),
        ItemInfo(110, "Nugget", "Cục Vàng Giá Trị", "Giá trị"),
        ItemInfo(111, "Heart Scale", "Vảy Trái Tim (Học lại chiêu)", "Giá trị"),

        // Đồ trang bị & Chiến đấu (179..225)
        ItemInfo(179, "BrightPowder", "Bột Phát Sáng (Tăng né đòn)", "Trang bị"),
        ItemInfo(180, "White Herb", "Thảo Dược Trắng", "Trang bị"),
        ItemInfo(181, "Macho Brace", "Vòng Tay Macho (x2 EV)", "Trang bị"),
        ItemInfo(182, "Exp. Share", "Chia Sẻ Kinh Nghiệm (Học EXP)", "Trang bị"),
        ItemInfo(183, "Quick Claw", "Vuốt Nhanh (Ưu tiên đi trước)", "Trang bị"),
        ItemInfo(184, "Soothe Bell", "Chuông Thân Thiện", "Trang bị"),
        ItemInfo(186, "Choice Band", "Băng Đeo Chọn Chiêu (+50% Atk)", "Trang bị"),
        ItemInfo(187, "King's Rock", "Đá Hoàng Đế (Gây nao núng)", "Trang bị"),
        ItemInfo(189, "Amulet Coin", "Đồng Xu May Mắn (x2 Tiền)", "Trang bị"),
        ItemInfo(190, "Cleanse Tag", "Bùa Tránh Đụng Độ", "Trang bị"),
        ItemInfo(191, "Soul Dew", "Giọt Sương Linh Hồn (Latios/Latias)", "Trang bị"),
        ItemInfo(195, "Everstone", "Đá Ngăn Tiến Hóa", "Trang bị"),
        ItemInfo(196, "Focus Band", "Băng Đeo Kiên Cường (Sống sót 1 HP)", "Trang bị"),
        ItemInfo(197, "Lucky Egg", "Trứng May Mắn (Tăng 150% EXP)", "Trang bị"),
        ItemInfo(198, "Scope Lens", "Kính Ngắm (Tăng Chí Mạng)", "Trang bị"),
        ItemInfo(199, "Metal Coat", "Lớp Phủ Kim Loại", "Trang bị"),
        ItemInfo(200, "Leftovers", "Thức Ăn Thừa (Hồi máu mỗi lượt)", "Trang bị"),
        ItemInfo(201, "Dragon Scale", "Vảy Rồng", "Trang bị"),
        ItemInfo(202, "Light Ball", "Bóng Phát Sáng (x2 Atk/SpA Pikachu)", "Trang bị"),
        ItemInfo(203, "Soft Sand", "Cát Mềm (Tăng chiêu Đất)", "Trang bị"),
        ItemInfo(204, "Hard Stone", "Đá Cứng (Tăng chiêu Đá)", "Trang bị"),
        ItemInfo(205, "Miracle Seed", "Hạt Giống Thần Kỳ (Tăng chiêu Cỏ)", "Trang bị"),
        ItemInfo(206, "BlackGlasses", "Kính Đen (Tăng chiêu Bóng Tối)", "Trang bị"),
        ItemInfo(207, "Black Belt", "Đai Đen (Tăng chiêu Giác Đấu)", "Trang bị"),
        ItemInfo(208, "Magnet", "Nam Châm (Tăng chiêu Điện)", "Trang bị"),
        ItemInfo(209, "Mystic Water", "Nước Thần Bí (Tăng chiêu Nước)", "Trang bị"),
        ItemInfo(210, "Sharp Beak", "Mỏ Nhọn (Tăng chiêu Bay)", "Trang bị"),
        ItemInfo(211, "Poison Barb", "Gai Độc (Tăng chiêu Độc)", "Trang bị"),
        ItemInfo(212, "NeverMeltIce", "Băng Không Tan (Tăng chiêu Băng)", "Trang bị"),
        ItemInfo(213, "Spell Tag", "Bùa Chú (Tăng chiêu Ma)", "Trang bị"),
        ItemInfo(214, "TwistedSpoon", "Thìa Cong (Tăng chiêu Siêu Linh)", "Trang bị"),
        ItemInfo(215, "Charcoal", "Than Củi (Tăng chiêu Lửa)", "Trang bị"),
        ItemInfo(216, "Dragon Fang", "Nanh Rồng (Tăng chiêu Rồng)", "Trang bị"),
        ItemInfo(217, "Silk Scarf", "Khăn Lụa (Tăng chiêu Thường)", "Trang bị"),
        ItemInfo(219, "Shell Bell", "Chuông Vỏ Sò (Hút máu)", "Trang bị")
    )

    private val idMap: Map<Int, ItemInfo> = items.associateBy { it.id }

    fun getItem(id: Int): ItemInfo? = idMap[id]

    fun getItemName(id: Int): String? = idMap[id]?.nameEn

    fun formatValue(value: Int, size: Int = 2): String {
        val hex = if (size == 1) {
            "0x" + (value and 0xFF).toString(16).uppercase().padStart(2, '0')
        } else if (size == 2) {
            "0x" + (value and 0xFFFF).toString(16).uppercase().padStart(4, '0')
        } else {
            "0x" + value.toLong().and(0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')
        }

        val item = if (size <= 2) idMap[value] else null
        return if (item != null) {
            "$value ($hex) • 🌟 ${item.nameEn} (${item.nameVi})"
        } else {
            "$value ($hex)"
        }
    }

    fun searchItems(query: String): List<ItemInfo> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return items
        return items.filter {
            it.nameEn.lowercase().contains(q) ||
            it.nameVi.lowercase().contains(q) ||
            it.id.toString() == q ||
            it.hexId.lowercase() == q ||
            it.category.lowercase().contains(q)
        }
    }

    fun getAllItems(): List<ItemInfo> = items
}
