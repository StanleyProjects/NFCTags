package sp.ax.nfctags

import android.nfc.Tag
import android.os.Bundle
import org.robolectric.util.ReflectionHelpers

internal fun mockTag(
    id: ByteArray = byteArrayOf(),
    techList: IntArray = intArrayOf(),
): Tag {
    return ReflectionHelpers.callStaticMethod(
        Tag::class.java,
        "createMockTag",
        ReflectionHelpers.ClassParameter.from(ByteArray::class.java, id),
        ReflectionHelpers.ClassParameter.from(IntArray::class.java, techList),
        ReflectionHelpers.ClassParameter.from(Array<Bundle>::class.java, arrayOf()),
    )
}
