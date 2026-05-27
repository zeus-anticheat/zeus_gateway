import net.minecraft.item.ItemStack;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.EnchantmentHelper;

public class test_enchantment {
    public void test(ItemStack itemStack) {
        ItemEnchantmentsComponent ec = itemStack.getEnchantments();
        EnchantmentHelper.getEnchantments(itemStack);
    }
}
