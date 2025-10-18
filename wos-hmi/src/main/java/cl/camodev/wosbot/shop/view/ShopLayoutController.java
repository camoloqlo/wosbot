package cl.camodev.wosbot.shop.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

public class ShopLayoutController extends AbstractProfileController {

	@FXML
	private CheckBox checkBoxNomadicMerchant, checkBoxNomadicMerchantVip,
			checkBoxBank, checkBoxMysteryShop, checkBoxMysteryShop50DiscountGear;

	@FXML
	private ComboBox<Integer> comboBoxBankDelay;

	@FXML
	private Label labelPeriod;

	@FXML
	private void initialize() {

		checkBoxMappings.put(checkBoxNomadicMerchant, EnumConfigurationKey.BOOL_NOMADIC_MERCHANT);
		checkBoxMappings.put(checkBoxNomadicMerchantVip, EnumConfigurationKey.BOOL_NOMADIC_MERCHANT_VIP_POINTS);
		checkBoxMappings.put(checkBoxBank, EnumConfigurationKey.BOOL_BANK);
		checkBoxMappings.put(checkBoxMysteryShop, EnumConfigurationKey.BOOL_MYSTERY_SHOP);
        checkBoxMappings.put(checkBoxMysteryShop50DiscountGear, EnumConfigurationKey.BOOL_MYSTERY_SHOP_250_HERO_WIDGET);

		// Initialize ComboBox with bank delay values
		comboBoxBankDelay.getItems().addAll(1, 7, 15, 30);
		comboBoxMappings.put(comboBoxBankDelay, EnumConfigurationKey.INT_BANK_DELAY);



		initializeChangeEvents();
	}

}
