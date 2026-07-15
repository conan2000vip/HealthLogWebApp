/* ==============================
   COMMON : 共通処理
============================== */

//hàm nhận vào 1 id và trả về HTML có id tương ứng(hàm tiện ích dùng để lấy phần tử theo HTML id)
function getElement(id) {
  return document.getElementById(id);
}

//khai báo hàm () bên trong khai báo hằng số biến này chứa 1 mảng gồm các id 
function clearErrors() {
  const errorIds = [ //khai báo hằng số danh sách các id dùng để hiển thị lỗi 
    "fruitNameError",
    "fruitRegionError", //hàm này tạo ra để xóa các thông báo lỗi đang hiển thị trước khi kiểm tra dữ liệu mới
    "fruitPriceError",
    "fruitQuantityError",
    "fruitImageError",
  ];

  //forEach nghĩa là duyệt quả từng phần tử trong mảng 
  errorIds.forEach((id) => {
    const el = getElement(id);
    if (el) { // nếu tìm thấy phần tử tưởng dương thì xóa nội dung bên trong
      el.textContent = "";
    }
  });
}

function setPreviewImage(imageUrl) {
  const previewImg = getElement("previewImg");//Khai báo hằng số previewImg. Gán cho nó phần tử HTML có id là previewImg.

  if (!previewImg) return; //Nếu không tìm thấy phần tử previewImg thì kết thúc hàm ngay.
  if (imageUrl) {
    // DBには images/xoai.jpg のような相対パスを保存する
    // 表示するときだけ /images/xoai.jpg にする
    previewImg.src = imageUrl.startsWith("/") ? imageUrl : "/" + imageUrl;
    previewImg.style.display = "block";//hiển thị ảnh
  } else { //nếu imageUrl null
    previewImg.removeAttribute("src");
    previewImg.style.display = "none";
  }
}
//lấy vùng miền đang được chọn
function getSelectedRegion() {
  const selectedRegion = document.querySelector('input[name="region"]:checked');
  return selectedRegion ? selectedRegion.value : "";
}
//đặt giá trị được chọn 
function setSelectedRegion(region) {
  document.querySelectorAll('input[name="region"]').forEach((radio) => {
    radio.checked = radio.value === region;
  });
}

/* ==============================
   SAVE : 商品登録・更新処理
   - フォーム値を収集
   - 既存画像URLを送信
   - SweetAlertで確認
   - バックエンドへPOST
   - バリデーションエラーを表示
============================== */

function saveFruit() {
  clearErrors();

  const id = getElement("fruitId").value;
  const currentImageUrl = getElement("currentImageUrl")
    ? getElement("currentImageUrl").value
    : "";

  const imageFile = getElement("fruitImage").files[0];
  const formData = new FormData();

  // IDがある場合だけ送る。空文字のIDを送るとBackend側でエラーになることがある
  if (id) {
    formData.append("id", id);
  }

  formData.append("name", getElement("fruitName").value);
  formData.append("region", getSelectedRegion());
  formData.append("price", getElement("fruitPrice").value);
  formData.append("quantity", getElement("fruitQuantity").value);
  formData.append("description", getElement("fruitDescription").value);

  // 重要：
  // 更新時に新しい画像を選ばない場合でも、古い画像URLを送る
  // previewImg.src ではなく、hiddenの currentImageUrl を使う
  formData.append("imageUrl", currentImageUrl);

  // 新しい画像が選択された場合だけ送信する
  if (imageFile) {
    formData.append("imageFile", imageFile);
  }

  const url = id ? "/fruits/update" : "/fruits/save";
  const actionText = id ? "更新" : "登録";

  Swal.fire({
    icon: "question",
    title: `商品を${actionText}しますか？`,
    showCancelButton: true,
    confirmButtonText: "OK",
    cancelButtonText: "キャンセル",
  }).then((result) => {
    if (!result.isConfirmed) return;

    fetch(url, {
      method: "POST",
      body: formData,
    })
      .then(async (response) => {
        if (!response.ok) {
          const errors = await response.json();

          console.log("ERRORS =", errors);

          getElement("fruitNameError").textContent = errors.name || "";
          getElement("fruitRegionError").textContent = errors.region || "";
          getElement("fruitPriceError").textContent = errors.price || "";
          getElement("fruitQuantityError").textContent = errors.quantity || "";
          getElement("fruitImageError").textContent =
            errors.imageFile || errors.imageUrl || "";

          if (errors.duplicate) {
            Swal.fire({
              icon: "warning",
              title: "重複エラー",
              text: errors.duplicate,
            });
          }

          // validation errorの場合は下のcatchで余計なエラーアラートを出さない
          throw { type: "validation" };
        }

        return response.text();
      })
      .then(() => {
        Swal.fire({
          icon: "success",
          title: id ? "商品情報を更新しました" : "商品を登録しました",
          confirmButtonText: "OK",
        }).then(() => {
          closeFruitForm();
          location.reload();
        });
      })
      .catch((error) => {
        console.error(error);

        if (error && error.type === "validation") {
          return;
        }
        Swal.fire({
          icon: "error",
          title: "処理失敗",
          text: "処理中にエラーが発生しました。",
        });
      });
  });
}

/* ==============================
   OPEN/CLOSE : モーダル表示・非表示処理
   - 商品追加ボタンで新規登録モードを開く
   - 編集ボタンで既存データをセットして編集モードを開く
   - 閉じるボタンでモーダルを消す
============================== */

function openAddForm() {
  // 新規登録モード: フォームをリセットし、画像プレビューをクリアする
  clearErrors();
  getElement("modalTitle").textContent = "商品登録";
  getElement("fruitForm").reset();
  getElement("fruitId").value = "";

  if (getElement("currentImageUrl")) {
    getElement("currentImageUrl").value = "";
  }
  const fruitImageEl = getElement("fruitImage");
  if (fruitImageEl) {
    fruitImageEl.value = "";
  }
  setPreviewImage("");
  const modal = getElement("fruitFormModal");
  if (modal) {
    modal.style.display = "flex";
  }
}

function openUpdateForm(btn) {
  // 編集モード: ボタンに埋め込まれたデータをフォームにセットする
  clearErrors();

  getElement("modalTitle").textContent = "商品編集";

  getElement("fruitId").value = btn.dataset.id || "";
  getElement("fruitName").value = btn.dataset.name || "";
  getElement("fruitPrice").value = btn.dataset.price || "";
  getElement("fruitQuantity").value = btn.dataset.quantity || "";
  getElement("fruitDescription").value = btn.dataset.description || "";

  setSelectedRegion(btn.dataset.region || "");

  // data-image-url でも data-imageurl でも取れるようにしておく
  const imageUrl = btn.dataset.imageUrl || btn.dataset.imageurl || "";

  // 古い画像URLをhiddenに保存する
  if (getElement("currentImageUrl")) {
    getElement("currentImageUrl").value = imageUrl;
  }

  // file input は必ず空にする
  // file inputには既存画像をセットできないため
  const fruitImageEl2 = getElement("fruitImage");
  if (fruitImageEl2) {
    fruitImageEl2.value = "";
  }

  setPreviewImage(imageUrl);

  const modal2 = getElement("fruitFormModal");
  if (modal2) {
    modal2.style.display = "flex";
  }
}

function closeFruitForm() {
  const modal = getElement("fruitFormModal");
  if (modal) {
    modal.style.display = "none";
  }
}

const imgButton = document.querySelector(".image_button");
if (imgButton) {
  imgButton.addEventListener("click", () => {
    const fruitImage = document.querySelector("#fruitImage");
    if (fruitImage) {
      fruitImage.click();
    }
  });
}

/* ==============================
   FORM INTERACTIONS : フォーム関連の処理
   - 入力変更でエラーメッセージを解除
   - 画像変更でプレビューを更新
   - region変更でエラーをクリア
============================== */
const clearFieldError = (fieldId) => {
  const errorEl = getElement(`${fieldId}Error`);
  if (errorEl) {
    errorEl.textContent = "";
  }
};

["fruitName", "fruitPrice", "fruitQuantity", "fruitDescription"].forEach(
  (fieldId) => {
    const field = getElement(fieldId);
    if (field) {
      field.addEventListener("input", () => clearFieldError(fieldId));
    }
  },
);

document.querySelectorAll('input[name="region"]').forEach((radio) => {
  radio.addEventListener("change", () => {
    clearFieldError("fruitRegion");
  });
});

const fruitImageInput = getElement("fruitImage");

if (fruitImageInput) {
  fruitImageInput.addEventListener("change", function () {
    const file = this.files[0];
    const currentImageUrl = getElement("currentImageUrl")
      ? getElement("currentImageUrl").value
      : "";

    if (file) {
      const previewImg = getElement("previewImg");
      previewImg.src = URL.createObjectURL(file);
      previewImg.style.display = "block";
    } else {
      setPreviewImage(currentImageUrl);
    }

    const imageError = getElement("fruitImageError");
    if (imageError) {
      imageError.textContent = "";
    }
  });
}

/* ==============================
   MODAL OUTSIDE CLICK : 外側クリックで閉じる
   - モーダル外をクリックしたら閉じる
============================== */

window.addEventListener("click", function (e) {
  const modal = getElement("fruitFormModal");

  if (e.target === modal) {
    closeFruitForm();
  }
});

/* ==============================
   DELETE : 商品削除処理
   - 削除ボタンで確認ダイアログを開く
   - OK で削除リクエストを送る
============================== */

function openConfirmDialog(id) {
  getElement("confirmDialog").style.display = "flex";
  getElement("confirmYes").dataset.id = id;
}

function closeConfirmDialog() {
  getElement("confirmDialog").style.display = "none";
}

const confirmYes = getElement("confirmYes");
const confirmNo = getElement("confirmNo");

if (confirmYes) {
  confirmYes.addEventListener("click", function () {
    window.location.href = "/fruits/delete/" + this.dataset.id;
  });
}

if (confirmNo) {
  confirmNo.addEventListener("click", function () {
    closeConfirmDialog();
  });
}

document.addEventListener("click", function (e) {
  if (e.target.classList.contains("delete-btn")) {
    openConfirmDialog(e.target.dataset.id);
  }
});
